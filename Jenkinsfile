pipeline {
    agent {
        docker {
            image 'maven:3.9.6-eclipse-temurin-21'
            args '-v $HOME/.m2:/var/maven/.m2'
        }
    }

    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=/var/maven/.m2/repository'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                dir('workload-ai-test-knative') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Test') {
            steps {
                dir('workload-ai-test-knative') {
                    sh 'mvn test'
                }
            }
        }
    }

    post {
        always {
            dir('workload-ai-test-knative') {
                junit 'target/surefire-reports/*.xml'
            }
        }
    }
}
