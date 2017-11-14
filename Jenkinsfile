node {
  ansiColor('xterm') {
    notifyFor() {
      deleteDir()
      withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NexusRepo', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {
  
        stage 'Checkout'
        checkout scm
  
        gitlabCommitStatus {
          docker.withRegistry('https://nexus.convergencelabs.tech:18443/', 'NexusRepo') {
            def sbtTools = docker.image('sbt-tools:0.6')
            sbtTools.pull()
  
            docker.image(sbtTools.imageName()).inside("-e nexus_realm='Sonatype Nexus Repository Manager' -e nexus_host=nexus.convergencelabs.tech -e nexus_user=$NEXUS_USER -e nexus_password=$NEXUS_PASSWORD") {
  		      stage 'Configure'
              sh '/usr/local/bin/confd -onetime -backend env'
  		
              stage 'Compile'
              sh 'sbt -d -J-Xmx3G -J-Xss5M compile'
  
              stage 'Test'
              sh 'sbt -d -J-Xmx3G -J-Xss5M test'
  		  
  		      stage 'Package'
              sh 'sbt package'
            
              stage 'Publish'
              sh 'sbt publish'
  
              stage 'Server Node Pack'
              sh 'sbt serverNode/pack'
            }
          }
  
          stage 'Server Node Docker (Dev)'
          echo "Current build number is ${env.BUILD_NUMBER}"
  
          sh '''
            echo "Creating docker target directory"
            cp -a server-node/src/docker/ server-node/target/docker
            cp -a server-node/target/pack server-node/target/docker/pack
  
            echo "Logging in to docker"
            docker login -u $NEXUS_USER -p $NEXUS_PASSWORD nexus.convergencelabs.tech:18444
  
            echo "Building the container"
            docker build -t nexus.convergencelabs.tech:18444/convergence-server-node server-node/target/docker 
  
            echo "Publishing the container"
            docker push nexus.convergencelabs.tech:18444/convergence-server-node
          '''
         }
       }
       deleteDir()
     }
   }
 }
