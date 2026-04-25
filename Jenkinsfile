pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-21-openjdk-amd64'
        MAVEN_OPTS = '-Dmaven.compiler.fork=true -Dmaven.compiler.executable=/usr/lib/jvm/java-21-openjdk-amd64/bin/javac'
        APP_PORT  = '8000'
        APP_JAR   = 'target/function-0.0.1-SNAPSHOT.jar'
        IMAGE_NAME = 'postbaby-mcp'
        CONTAINER_NAME = 'postbaby-mcp'
    }

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Initialize') {
            steps {
                echo "--- Initializing Build #${BUILD_NUMBER} ---"
                sh '"${JAVA_HOME}/bin/javac" -version'
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
                sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('Deploy') {
            steps {
                echo "--- Deploying application ---"

                // Stop and remove old container if running
                sh "docker stop ${CONTAINER_NAME} || true"
                sh "docker rm ${CONTAINER_NAME} || true"

                // Run new container
                sh """
                    docker run -d \
                        --name ${CONTAINER_NAME} \
                        --restart always \
                        -p ${APP_PORT}:${APP_PORT} \
                        ${IMAGE_NAME}:${BUILD_NUMBER}
                """

                // Wait for app to start, then health check
                sh "sleep 10"
                sh "curl -sf http://localhost:${APP_PORT}/actuator/health || (docker logs ${CONTAINER_NAME} && exit 1)"

                echo "--- App is running on port ${APP_PORT} ---"
            }
        }

        stage('Cleanup') {
            steps {
                echo "--- Cleaning up old Docker images ---"
                sh "docker image prune -f"
                sh "docker images ${IMAGE_NAME} --format '{{.Tag}}' | grep -v latest | sort -rn | tail -n +6 | xargs -I {} docker rmi ${IMAGE_NAME}:{} || true"
            }
        }
    }

    post {
        always {
            echo "--- Pipeline Finished: Build #${BUILD_NUMBER} ---"
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
        }
        success {
            echo "SUCCESS: Build #${BUILD_NUMBER} deployed successfully!"
        }
        failure {
            echo "FAILURE: Build #${BUILD_NUMBER} failed. Collecting logs..."
            sh "docker logs ${CONTAINER_NAME} || true"
        }
        unstable {
            echo "UNSTABLE: Build #${BUILD_NUMBER} has test failures."
        }
    }
}