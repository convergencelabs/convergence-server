node {
  ansiColor('xterm') {
    notifyFor() {
      deleteDir()
      withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NexusRepo', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
        def scmVars
        stage('Checkout') {
          scmVars = checkout scm
        }
  
        gitlabCommitStatus {
          docker.withRegistry('https://nexus.convergencelabs.tech:18443/', 'NexusRepo') {
            docker.image('sbt-tools:0.7.2')
              .inside("-e nexus_realm='Sonatype Nexus Repository Manager' -e nexus_host=nexus.convergencelabs.tech -e nexus_user=$NEXUS_USER -e nexus_password=$NEXUS_PASSWORD") {
  		      stage('Configure') {
                sh '/usr/local/bin/confd -onetime -backend env'
              }
  		
              stage('Compile') {
                sh 'sbt -d -J-Xmx3G -J-Xss5M compile'
              }
  
              stage('Test') {
                sh 'sbt -d -J-Xmx3G -J-Xss5M test'
              }
  		  
  		      stage('Package') {
                sh 'sbt package'
              }
            
              stage('Publish') { 
                sh 'sbt publish'
              }
  
              stage('Sbt Pack') {
                sh 'sbt serverNode/pack'
              }
            }
          }
          
          def img
          stage('Docker Build') { 
            sh '''
            cp -a server-node/src/docker/ server-node/target/docker
            cp -a server-node/target/pack server-node/target/docker/pack
            '''
          
            dir("server-node/target/docker")
            img = docker.build("convergence-server-node")
          }
       
          stage('Docker Push') { 
            docker.withRegistry('https://nexus.convergencelabs.tech:18444', 'NexusRepo') {
              img.push("build${env.BUILD_NUMBER}")
              img.push("${scmVars.GIT_COMMIT}")
              img.push("latest")
           }
          }
        }
      }
      deleteDir()
    }
  }
}
