server = Artifactory.server "artifactory"
rtFullUrl = server.url

podTemplate(label: 'helm-template' , cloud: 'k8s' , containers: [
        containerTemplate(name: 'jfrog-cli', image: 'docker.bintray.io/jfrog/jfrog-cli-go:latest', command: 'cat', ttyEnabled: true) ,
        containerTemplate(name: 'helm', image: 'alpine/helm:latest', command: 'cat', ttyEnabled: true) ]) {

    node('helm-template') {
        stage('Build Chart & push it to Artifactory') {

            git url: 'https://github.com/eladh/docker-app-demo.git', credentialsId: 'github'
            def pipelineUtils = load 'pipelineUtils.groovy'

            def aqlString = 'items.find ({"repo":"docker-local","type":"folder","$and":' +
                    '[{"path":{"$match":"docker-app*"}},{"path":{"$nmatch":"docker-app/latest"}}]' +
                    '}).include("path","created","name").sort({"$desc" : ["created"]}).limit(1)'


            def artifactInfo = pipelineUtils.executeAql(rtFullUrl, aqlString)
            def dockerTag = artifactInfo ? artifactInfo.name : "latest"

            println dockerTag

            container('helm') {
                sh "helm init --client-only"
                sh "sed -i 's/latest/${dockerTag}/g' helm-chart-docker-app/values.yaml"
                sh "helm package helm-chart-docker-app"
            }
            container('jfrog-cli') {
                pipelineUtils.pushHelmChart("helm-local" ,"helm-chart-docker-app" ,dockerTag)
            }
        }
    }
}