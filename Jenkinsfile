pipeline {
    agent any

    tools {
        jdk 'JDK17'  
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Initialize') {
            steps {
                echo "--- Initializing Build ---"
                echo "Workspace: ${env.WORKSPACE}"
                echo "Build Number: ${env.BUILD_NUMBER}"
                sh 'java -version'   // should now show 17
                sh 'chmod +x mvnw'
                sh './mvnw -version'
            }
        }

        stage('Checkout') {
            steps {
                echo "--- Checking out source code ---"
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo "--- Building project ---"
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                echo "--- Running tests ---"
                sh './mvnw test'
            }
        }
    }

    post {
        always {
            echo "--- Execution Finished ---"
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
        }
        success {
            echo "SUCCESS: Build and tests passed!"
        }
        failure {
            echo "FAILURE: Build or tests failed. Check logs for details."
        }
        unstable {
            echo "UNSTABLE: Some tests failed."
        }
    }
}
