pipeline {
    agent {
        label 'linux'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout(true)
    }

    tools {
        jdk   'JDK-21'
    }

    parameters {
        string(
            name:         'SONAR_PROJECT_KEY',
            defaultValue: 'EPM-ICMP-JAN-2026-JAVA-TEAM1',
            description:  'SonarQube project key'
        )
    }

    environment {
        // ── SonarQube ─────────────────────────────────────────────────────────
        SONAR_HOST_URL  = 'https://sonarhyd.epam.com'
        JACOCO_XML_PATH = 'codeval-service/target/site/jacoco/jacoco.xml'
    }

    stages {

        // ── Stage 1: Clean Workspace ──────────────────────────────────────────
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        // ── Stage 2: Checkout ─────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                sh 'cd codeval-service && chmod +x mvnw'
            }
        }

        // ── Stage 3: Compile ──────────────────────────────────────────────────
        stage('Compile') {
            steps {
                sh 'cd codeval-service && ./mvnw -B compile'
            }
        }

        // ── Stage 4: Unit Tests ───────────────────────────────────────────────
        stage('Unit Tests') {
            steps {
                sh 'cd codeval-service && ./mvnw -B test'
            }
            post {
                always {
                    junit testResults: 'codeval-service/**/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── Stage 5: Code Coverage (JaCoCo) ──────────────────────────────────
        stage('Code Coverage') {
            steps {
                sh 'cd codeval-service && ./mvnw -B verify'
            }
        }

        // ── Stage 6: SonarQube Analysis ───────────────────────────────────────
        // Expects the coverage stage to run first so jacoco.xml exists.
        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'Animesh-sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('SonarHyd') {
                        sh """
                            cd codeval-service && ./mvnw -B sonar:sonar \\
                              -Dsonar.projectKey=${params.SONAR_PROJECT_KEY} \\
                              -Dsonar.host.url=${SONAR_HOST_URL} \\
                              -Dsonar.token=${SONAR_TOKEN} \\
                              -Dsonar.coverage.jacoco.xmlReportPaths=${JACOCO_XML_PATH}
                        """
                    }
                }
            }
        }

        // ── Stage 7: Quality Gate ─────────────────────────────────────────────
        // Waits for SonarQube webhook callback and aborts if gate fails.
        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

    }

    post {
        success {
            echo "✅ CI successful — Branch: ${env.BRANCH_NAME} | Build: #${env.BUILD_NUMBER}"
        }
        failure {
            echo "❌ Pipeline failed — Branch: ${env.BRANCH_NAME} | Build: #${env.BUILD_NUMBER}"
        }
        always {
            cleanWs()
        }
    }
}