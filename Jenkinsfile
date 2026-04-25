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

        stage('Deploy') {
            steps {
                echo "--- Deploying application ---"
                sh 'fuser -k ${APP_PORT}/tcp || true'
                sh 'BUILD_ID=dontKillMe nohup $JAVA_HOME/bin/java -jar ${APP_JAR} --server.port=${APP_PORT} --server.address=0.0.0.0 > app.log 2>&1 &'
                sh 'sleep 10'
                sh 'curl -v http://localhost:${APP_PORT}/actuator/health || (cat app.log && exit 1)'
                echo "--- App is running on port ${APP_PORT} ---"
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