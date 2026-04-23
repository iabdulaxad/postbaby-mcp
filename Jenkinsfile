pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    tools {
        maven 'maven-3.9.6'
        jdk 'jdk-21'
    }

    environment {
        // MAVEN_OPTS = '-Dmaven.repo.local=/var/maven/.m2/repository'
    }

    stages {
        stage('Initialize') {
            steps {
                echo "--- Initializing Build ---"
                echo "Workspace: ${env.WORKSPACE}"
                echo "Build Number: ${env.BUILD_NUMBER}"
                sh 'java -version'
                sh 'mvn -version'
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
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                echo "--- Running tests ---"
                sh 'mvn test'
            }
        }
    }

    post {
        always {
            echo "--- Execution Finished ---"
            junit 'target/surefire-reports/*.xml'
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
