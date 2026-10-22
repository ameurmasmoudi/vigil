pipeline{
  agent {label 'docker-agent'}
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 30, unit: 'MINUTES')
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
            dir("api"){
              withSonarQubeEnv("sonarqube"){
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
            dir("worker"){
              withSonarQubeEnv("sonarqube"){
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
    stage("trivy"){
      stages{
        stage('FS Scan'){
          steps{
            sh 'trivy fs .'
          }
        }
        stage('building the db and java-db'){
          steps{
            sh 'trivy image --timeout 15m --download-db-only'
            sh 'trivy image --timeout 15m --download-java-db-only'
          }
        }
        stage('build and scan'){
          matrix{
            axes{
              axis{
                name "APP";
                values 'api','worker'
              }
            }
            environment{
              IMAGE = "${APP}:${env.GIT_COMMIT}"
            }
            stages{
              stage('building image'){
                steps{
                  dir("${APP}"){
                    sh "docker build --pull . -t ${IMAGE}"
                  }
                }
              }
              stage('trivy image scan'){
                steps{
                    sh "trivy image --skip-db-update --skip-java-db-update --cache-backend memory ${IMAGE} -f table -o 'trivy-${APP}.txt'"
                    sh "trivy image --skip-db-update --skip-java-db-update --cache-backend memory --ignore-unfixed --exit-code 1 --severity HIGH,CRITICAL ${IMAGE}"
                }
                post{
                  always{
                    archiveArtifacts artifacts: "trivy-${APP}.txt" ,allowEmptyArchive: true
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
