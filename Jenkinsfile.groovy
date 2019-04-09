server = Artifactory.server "artifactory"
rtFullUrl = server.url

podTemplate(label: 'helm-template' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'jfrog-cli', image: 'docker.bintray.io/jfrog/jfrog-cli-go:latest', command: 'cat', ttyEnabled: true) ,
        containerTemplate(name: 'helm', image: 'alpine/helm:latest', command: 'cat', ttyEnabled: true) ]) {

    node('helm-template') {
        stage('Build Chart & push it to Artifactory') {
            git url: 'https://github.com/eladh/helm-app-demo.git', credentialsId: 'github'
            sh "./update_version.sh helm-chart-docker-app/Chart.yaml patch"

            def pipelineUtils = load 'pipelineUtils.groovy'

            def aqlString = 'items.find ({"repo":"docker-local","type":"folder","$and":' +
                    '[{"path":{"$match":"docker-app*"}},{"path":{"$nmatch":"docker-app/latest"}}]' +
                    '}).include("path","created","name").sort({"$desc" : ["created"]}).limit(1)'


            def artifactInfo = pipelineUtils.executeAql(rtFullUrl, aqlString)
            def dockerTag = artifactInfo ? artifactInfo.name : "latest"

            container('helm') {
                sh "helm init --client-only"
                sh "sed -i 's/latest/${dockerTag}/g' helm-chart-docker-app/values.yaml"
                sh "helm package helm-chart-docker-app"
            }
            container('jfrog-cli') {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'artifactorypass', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    sh "jfrog rt c beta --user ${USERNAME} --password ${PASSWORD} --url ${rtFullUrl} < /dev/null"
                    sh "jfrog rt u '*.tgz' helm-local --build-name=${env.JOB_NAME} --build-number=${env.BUILD_NUMBER} -server-id beta --props='release-bundle=true'"
                    sh "jfrog rt bce ${env.JOB_NAME} ${env.BUILD_NUMBER} "

                    sh "jfrog rt dl docker-prod-local/docker-app/${dockerTag}/manifest.json --build-name=${env.JOB_NAME} --build-number=${env.BUILD_NUMBER} -server-id beta"
                    sh "jfrog rt bp ${env.JOB_NAME} ${env.BUILD_NUMBER} -server-id beta"
                }
            }
        }
    }
}