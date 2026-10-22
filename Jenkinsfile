pipeline{
  agent {label 'docker-agent'}
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 20, unit: 'MINUTES')
  }
  stages {
    stage('test'){
      matrix {
        axes {
          axis{
            name "APP";
            values 'api','worker'
          }
        }
        stages{
          stage('running tests'){
            steps{
              dir("${APP}"){
                  sh './mvnw verify -B --no-transfer-progress'
              }
            }
            post{
              always {
                dir("${APP}"){
                  junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
              }
            }
          }
          
        }
      }
    }
    stage("sonarqube"){
      stages{
        stage("api sonar"){
          steps{
            withSonarQubeEnv("sonarqube"){
              dir("api"){
                sh './mvnw sonar:sonar -B --no-transfer-progress'
              }
            }
            timeout(time:3, unit: 'MINUTES'){
              waitForQualityGate abortPipeline: true
            }
          }
        }
        stage("worker sonar"){
          steps{
            withSonarQubeEnv("sonarqube"){
              dir("worker"){
                sh './mvnw sonar:sonar -B --no-transfer-progress'
              }
            }
            timeout(time:3, unit: 'MINUTES'){
              waitForQualityGate abortPipeline: true
            }
          } 
        }
      }
    }
  }
}
