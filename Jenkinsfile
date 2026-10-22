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
                    sh "docker build . -t ${IMAGE}"
                  }
                }
              }
              stage('trivy image scan'){
                steps{
                    sh "trivy image ${IMAGE} -f table -o 'trivy-${APP}.txt'"
                    sh "trivy image --ignore-unfixed --exit-code 1 --severity HIGH,CRITICAL ${IMAGE}"
                }
                post{
                  always{
                    archiveArtifacts artifacts: "trivy-${APP}.txt" ,allowEmpltyArchive: true
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
