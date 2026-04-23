pipeline {
    agent any

    environment {
        JAVA_HOME        = '/usr/lib/jvm/java-21-openjdk-amd64'
        MAVEN_COMPILER   = '/usr/lib/jvm/java-21-openjdk-amd64/bin/javac'
        MAVEN_OPTS       = '-Dmaven.compiler.fork=true -Dmaven.compiler.executable=/usr/lib/jvm/java-21-openjdk-amd64/bin/javac'
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
                sh 'echo "JAVA_HOME=$JAVA_HOME"'
                sh '$JAVA_HOME/bin/javac -version'   // verify directly
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
        success { echo "SUCCESS: Build and tests passed!" }
        failure { echo "FAILURE: Build or tests failed. Check logs for details." }
        unstable { echo "UNSTABLE: Some tests failed." }
    }
}