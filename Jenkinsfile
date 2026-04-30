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

    parameters {
        // ── CI Stage Toggles ──────────────────────────────────────────────────
        booleanParam(name: 'RUN_UNIT_TESTS',        defaultValue: true,  description: 'Run Unit Tests (JUnit 5 + Mockito)')
        booleanParam(name: 'RUN_INTEGRATION_TESTS', defaultValue: true,  description: 'Run Integration Tests (Testcontainers) — also requires develop or release/* branch')
        booleanParam(name: 'RUN_COVERAGE',          defaultValue: true,  description: 'Run Code Coverage (JaCoCo) — MUST be true for SonarQube to show coverage data')
        booleanParam(name: 'RUN_SONAR',             defaultValue: true,  description: 'Run SonarQube Analysis + Quality Gate')
        booleanParam(name: 'RUN_BUILD_JAR',         defaultValue: true,  description: 'Run Build JAR (Maven package)')

        // ── CD Stage Toggles ──────────────────────────────────────────────────
        booleanParam(name: 'RUN_DOCKER_BUILD',      defaultValue: true,  description: 'Run Docker Image Build')
        booleanParam(name: 'RUN_DEPLOY',            defaultValue: true,  description: 'Run Deploy to ECS (only on main/master/release/* branches)')

        // ── Overridable Values ────────────────────────────────────────────────
        string(
            name:         'COVERAGE_THRESHOLD',
            defaultValue: '70',
            description:  'Minimum line coverage % required (default: 70)'
        )
        string(
            name:         'SONAR_PROJECT_KEY',
            defaultValue: 'EPM-ICMP-JAN-2026-JAVA-TEAM2',
            description:  'SonarQube project key'
        )
    }

    environment {
        // ── SonarQube ─────────────────────────────────────────────────────────
        SONAR_HOST_URL  = 'https://sonarhyd.epam.com'
        SONAR_TOKEN     = credentials('Sonar-Muzammil')
        JACOCO_XML_PATH = 'executionEngine-service/target/site/jacoco/jacoco.xml'

        // ── AWS / ECS ─────────────────────────────────────────────────────────
        AWS_REGION         = 'ap-south-1'
        AWS_DEFAULT_REGION = 'ap-south-1'

        ACCOUNT_ID         = '169984788524'
        ECR_REPO           = "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/my-app"

        ECS_CLUSTER        = 'my-cluster'
        ECS_SERVICE        = 'my-service'
        TASK_FAMILY        = 'my-task'

        EXECUTION_ROLE_ARN = "arn:aws:iam::${ACCOUNT_ID}:role/ecsTaskExecutionRole"
        TASK_ROLE_ARN      = "arn:aws:iam::${ACCOUNT_ID}:role/ecsTaskRole"

        IMAGE_TAG          = "${env.BUILD_NUMBER}"
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
            }
        }

        // ── Stage 3: Branch Info ──────────────────────────────────────────────
        // Detects branch name and decides whether CD stages should run.
        // Works for both Multibranch Pipeline (BRANCH_NAME) and
        // regular Pipeline jobs (git log fallback).
        stage('Branch Info') {
            steps {
                script {
                    // Try BRANCH_NAME first (Multibranch), fall back to git log (regular Pipeline)
                    if (env.BRANCH_NAME) {
                        env.GIT_BRANCH_NAME = env.BRANCH_NAME
                    } else {
                        env.GIT_BRANCH_NAME = sh(
                            script: 'git log -1 --format=%D | grep -oP "origin/\\K[^ ,]+"',
                            returnStdout: true
                        ).trim()
                    }

                    env.IS_DEPLOY_BRANCH = (env.GIT_BRANCH_NAME ==~ /(main|master|release\/.*)/) ? 'true' : 'false'

                    echo "Branch      : ${env.GIT_BRANCH_NAME}"
                    echo "Commit      : ${env.GIT_COMMIT ?: 'N/A'}"
                    echo "Deploy CD   : ${env.IS_DEPLOY_BRANCH}"
                    echo "Build #     : ${env.BUILD_NUMBER}"
                }
            }
        }

        // ── Stage 4: Compile ──────────────────────────────────────────────────
        stage('Compile') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'cd executionEngine-service && ./mvnw -B compile'
                    } else {
                        bat 'cd executionEngine-service && mvnw.cmd -B compile'
                    }
                }
            }
        }

        // ── Stage 5: Unit Tests ───────────────────────────────────────────────
        stage('Unit Tests') {
            when {
                expression { return params.RUN_UNIT_TESTS }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh 'cd executionEngine-service && ./mvnw -B test'
                    } else {
                        bat 'cd executionEngine-service && mvnw.cmd -B test'
                    }
                }
            }
            post {
                always {
                    junit testResults: 'executionEngine-service/**/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── Stage 6: Integration Tests ────────────────────────────────────────
        // Runs ONLY when:
        //   (a) RUN_INTEGRATION_TESTS toggle is true, AND
        //   (b) branch is develop or release/*
        stage('Integration Tests') {
            when {
                allOf {
                    expression { return params.RUN_INTEGRATION_TESTS }
                    anyOf {
                        branch 'develop'
                        branch 'release/*'
                    }
                }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh 'cd executionEngine-service && ./mvnw -B verify -P integration-tests'
                    } else {
                        bat 'cd executionEngine-service && mvnw.cmd -B verify -P integration-tests'
                    }
                }
            }
            post {
                always {
                    junit testResults: 'executionEngine-service/**/target/failsafe-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── Stage 7: Code Coverage (JaCoCo) ──────────────────────────────────
        stage('Code Coverage') {
            when {
                expression { return params.RUN_COVERAGE }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh 'cd executionEngine-service && ./mvnw -B verify jacoco:report'
                    } else {
                        bat 'cd executionEngine-service && mvnw.cmd -B verify jacoco:report'
                    }
                }
            }
        }

        // ── Stage 8: SonarQube Analysis ───────────────────────────────────────
        // ⚠️  RUN_COVERAGE must be true otherwise jacoco.xml won't exist
        //     and SonarQube will show 0% coverage.
        stage('SonarQube Analysis') {
            when {
                expression { return params.RUN_SONAR }
            }
            steps {
                script {
                    if (!params.RUN_COVERAGE) {
                        echo "⚠️ WARNING: RUN_COVERAGE is false — coverage will be 0% in SonarQube"
                    }
                }
                withCredentials([string(credentialsId: 'Sonar-Muzammil', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('SonarHyd') {
                        script {
                            if (isUnix()) {
                                sh """
                                    cd executionEngine-service && ./mvnw -B sonar:sonar \\
                                      -Dsonar.projectKey=${params.SONAR_PROJECT_KEY} \\
                                      -Dsonar.host.url=${SONAR_HOST_URL} \\
                                      -Dsonar.token=${SONAR_TOKEN} \\
                                      -Dsonar.coverage.jacoco.xmlReportPaths=${JACOCO_XML_PATH}
                                """
                            } else {
                                bat """
                                    cd executionEngine-service && mvnw.cmd -B sonar:sonar ^
                                      -Dsonar.projectKey=${params.SONAR_PROJECT_KEY} ^
                                      -Dsonar.host.url=${SONAR_HOST_URL} ^
                                      -Dsonar.token=%SONAR_TOKEN% ^
                                      -Dsonar.coverage.jacoco.xmlReportPaths=${JACOCO_XML_PATH}
                                """
                            }
                        }
                    }
                }
            }
        }

        // ── Stage 9: Quality Gate ─────────────────────────────────────────────
        // Waits for SonarQube webhook callback and aborts if gate fails.
        // Pipeline will NOT proceed to Docker/deploy if quality gate fails.
        stage('Quality Gate') {
            when {
                expression { return params.RUN_SONAR }
            }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ── Stage 10: Build JAR ───────────────────────────────────────────────
        stage('Build JAR') {
            when {
                expression { return params.RUN_BUILD_JAR }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh 'cd executionEngine-service && ./mvnw -B package -DskipTests'
                    } else {
                        bat 'cd executionEngine-service && mvnw.cmd -B package -DskipTests'
                    }
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'executionEngine-service/target/*.jar', fingerprint: true
                }
            }
        }

        // ── Stage 11: Archive Coverage Report ────────────────────────────────
        stage('Archive Coverage Report') {
            when {
                expression { return params.RUN_COVERAGE }
            }
            steps {
                archiveArtifacts artifacts: 'executionEngine-service/target/site/jacoco/**',
                                 fingerprint: true
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        //  CD STAGES — only run on main / master / release/* branches
        //              AND when RUN_DEPLOY parameter is true
        // ─────────────────────────────────────────────────────────────────────

        // ── Stage 12: Build Docker Image ──────────────────────────────────────
        stage('Build Docker Image') {
            when {
                allOf {
                    expression { return params.RUN_DOCKER_BUILD }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                sh '''
                chmod +x executionEngine-service/mvnw
                docker build -t my-app:$IMAGE_TAG .
                '''
            }
        }

        // ── Stage 13: Push to ECR ─────────────────────────────────────────────
        stage('Push to ECR') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-DEP-team2-creds'
                ]]) {
                    sh '''
                    echo "Logging into ECR..."
                    aws ecr get-login-password --region $AWS_REGION \
                    | docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

                    echo "Tagging image..."
                    docker tag my-app:$IMAGE_TAG $ECR_REPO:$IMAGE_TAG

                    echo "Pushing image..."
                    docker push $ECR_REPO:$IMAGE_TAG
                    '''
                }
            }
        }

        // ── Stage 14: Prepare Task Definition ────────────────────────────────
        stage('Prepare Task Definition') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                script {
                    def taskDef = """{
  "family": "${env.TASK_FAMILY}",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "${env.EXECUTION_ROLE_ARN}",
  "taskRoleArn": "${env.TASK_ROLE_ARN}",
  "containerDefinitions": [
    {
      "name": "my-app",
      "image": "${env.ECR_REPO}:${env.IMAGE_TAG}",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "essential": true,
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/my-task",
          "awslogs-region": "${env.AWS_REGION}",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "wget -q -O - http://localhost:8080/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}"""
                    writeFile file: 'task-def.json', text: taskDef
                    sh '''
                    echo "====== TASK DEF ======"
                    cat task-def.json
                    echo "======================"
                    '''
                }
            }
        }

        // ── Stage 15: Register Task Definition ───────────────────────────────
        stage('Register Task Definition') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-DEP-team2-creds'
                ]]) {
                    script {
                        env.TASK_REVISION = sh(
                            script: '''
                            aws ecs register-task-definition \
                                --region $AWS_REGION \
                                --cli-input-json file://task-def.json \
                                --query 'taskDefinition.revision' \
                                --output text
                            ''',
                            returnStdout: true
                        ).trim()

                        echo "Registered Task Revision: ${env.TASK_REVISION}"
                    }
                }
            }
        }

        // ── Stage 16: Deploy to ECS ───────────────────────────────────────────
        stage('Deploy to ECS') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-DEP-team2-creds'
                ]]) {
                    sh """
                    echo "Deploying task def: ${env.TASK_FAMILY}:${env.TASK_REVISION}"
                    aws ecs update-service \
                        --region ${env.AWS_REGION} \
                        --cluster ${env.ECS_CLUSTER} \
                        --service ${env.ECS_SERVICE} \
                        --task-definition ${env.TASK_FAMILY}:${env.TASK_REVISION} \
                        --health-check-grace-period-seconds 120 \
                        --force-new-deployment
                    """
                }
            }
        }

        // ── Stage 17: Wait for Stable Deployment ─────────────────────────────
        stage('Wait for Deployment') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-DEP-team2-creds'
                ]]) {
                    sh '''
                    echo "Waiting for ECS service to stabilize..."
                    aws ecs wait services-stable \
                        --region $AWS_REGION \
                        --cluster $ECS_CLUSTER \
                        --services $ECS_SERVICE
                    '''
                }
            }
        }

        // ── Stage 18: Cleanup ─────────────────────────────────────────────────
        stage('Cleanup') {
            steps {
                sh 'docker image prune -f'
            }
        }
    }

    post {
        success {
            script {
                if (env.IS_DEPLOY_BRANCH == 'true' && params.RUN_DEPLOY) {
                    echo "✅ CI + CD successful — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER} | Task: ${env.TASK_FAMILY}:${env.TASK_REVISION}"
                } else {
                    echo "✅ CI successful — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER} (CD skipped)"
                }
            }
        }
        failure {
            echo "❌ Pipeline failed — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER}"
        }
        always {
            cleanWs()
        }
    }
}