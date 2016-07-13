node {
  withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'NexusRepo', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASSWORD']]) {

    stage 'Checkout'
    checkout scm

    gitlabCommitStatus {
      docker.withRegistry('https://nexus.convergencelabs.tech:18443/', 'NexusRepo') {
        def sbtTools = docker.image('sbt-tools')
        sbtTools.pull()

        docker.image(sbtTools.imageName()).inside {
          stage 'Compile'
          sh 'sbt compile'

          stage 'Test'
          sh 'sbt test'

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

        docker.withRegistry('https://nexus.convergencelabs.tech:18444/', 'NexusRepo') {
          echo "Building the container"
          docker.build('convergence-server-node-test', 'server-node/target/docker/').push('latest')
       }
      '''
     }
   }
 }