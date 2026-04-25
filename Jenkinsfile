pipeline {
    agent any

    environment {
        JAVA_HOME      = '/usr/lib/jvm/java-21-openjdk-amd64'
        MAVEN_COMPILER = '/usr/lib/jvm/java-21-openjdk-amd64/bin/javac'
        MAVEN_OPTS     = '-Dmaven.compiler.fork=true -Dmaven.compiler.executable=/usr/lib/jvm/java-21-openjdk-amd64/bin/javac'
        APP_PORT       = '8000'
        APP_JAR        = 'target/function-0.0.1-SNAPSHOT.jar'
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
                sh '$JAVA_HOME/bin/javac -version'
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

        stage('Build Image') {
            steps {
                echo "--- Building Docker Image ---"
                sh 'docker build -t postbaby-mcp:${BUILD_NUMBER} .'
            }
        }

        stage('Deploy') {
            steps {
                echo "--- Deploying application to Docker ---"
                sh 'docker stop postbaby-mcp || true'
                sh 'docker rm postbaby-mcp || true'
                sh '''
                    docker run -d \
                        --name postbaby-mcp \
                        --restart always \
                        -p 8000:8000 \
                        postbaby-mcp:${BUILD_NUMBER}
                '''
                sh 'sleep 10'
                sh 'curl -v http://localhost:8000/actuator/health || (docker logs postbaby-mcp && exit 1)'
                echo "--- App is running on port 8000 ---"
            }
        }
    }

    post {
        always {
            echo "--- Execution Finished ---"
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/*.jar, app.log', allowEmptyArchive: true
        }
        success { echo "SUCCESS: Build, tests, and deployment passed!" }
        failure { echo "FAILURE: Something failed. Check logs for details." }
        unstable { echo "UNSTABLE: Some tests failed." }
    }
}