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
        booleanParam(name: 'RUN_INTEGRATION_TESTS', defaultValue: true,  description: 'Run Integration Tests (Testcontainers) — also requires develop, release/*, or main branch')
        booleanParam(name: 'RUN_COVERAGE',          defaultValue: true,  description: 'Run Code Coverage (JaCoCo) — MUST be true for SonarQube to show coverage data')
        booleanParam(name: 'RUN_SONAR',             defaultValue: true,  description: 'Run SonarQube Analysis + Quality Gate')
        booleanParam(name: 'RUN_BUILD_JAR',         defaultValue: true,  description: 'Run Build JAR (Maven package)')

        // ── CD Stage Toggles ──────────────────────────────────────────────────
        booleanParam(name: 'RUN_DEPLOY',            defaultValue: true,  description: 'Run Deploy to ECS (only on develop, release/*, and main/master branches)')
        booleanParam(name: 'RUN_PROD_APPROVAL',     defaultValue: true,  description: 'Require manual approval for PROD deployment (main/master branches only)')

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
        SONAR_TOKEN     = credentials('java-team2-sonar-token')
        JACOCO_XML_PATH = 'executionEngine-service/target/site/jacoco/jacoco.xml'

        // ── AWS / ECS (Common) ────────────────────────────────────────────────
        AWS_REGION         = 'ap-south-1'
        AWS_DEFAULT_REGION = 'ap-south-1'

        ACCOUNT_ID         = '169984788524'
        ECR_REPO           = "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/my-app"

        EXECUTION_ROLE_ARN = "arn:aws:iam::${ACCOUNT_ID}:role/ecsTaskExecutionRole"
        TASK_ROLE_ARN      = "arn:aws:iam::${ACCOUNT_ID}:role/ecsTaskRole"
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
        stage('Branch Info') {
            steps {
                script {
                    if (env.BRANCH_NAME) {
                        env.GIT_BRANCH_NAME = env.BRANCH_NAME
                    } else {
                        env.GIT_BRANCH_NAME = sh(
                            script: 'git log -1 --format=%D | grep -oP "origin/\\K[^ ,]+"',
                            returnStdout: true
                        ).trim()
                    }

                    // Treat main, master, develop, release, and release/* as deployable
                    env.IS_DEPLOY_BRANCH = (env.GIT_BRANCH_NAME ==~ /^(main|master|develop|release(\/.*)?)$/) ? 'true' : 'false'

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
                // Ensure the maven wrapper is executable on the Linux agent
                sh 'chmod +x executionEngine-service/mvnw'
                sh 'cd executionEngine-service && ./mvnw -B compile'
            }
        }

        // ── Stage 5: Unit Tests ───────────────────────────────────────────────
        stage('Unit Tests') {
            when {
                expression { return params.RUN_UNIT_TESTS }
            }
            steps {
                sh 'cd executionEngine-service && ./mvnw -B test'
            }
            post {
                always {
                    junit testResults: 'executionEngine-service/**/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── Stage 6: Integration Tests ────────────────────────────────────────
        stage('Integration Tests') {
            when {
                allOf {
                    expression { return params.RUN_INTEGRATION_TESTS }
                    anyOf {
                        branch 'develop'
                        branch 'release/*'
                        branch 'release'
                        branch 'main'
                        branch 'master'
                    }
                }
            }
            steps {
                sh 'cd executionEngine-service && ./mvnw -B verify -P integration-tests'
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
                sh 'cd executionEngine-service && ./mvnw -B verify jacoco:report'
            }
        }

        // ── Stage 8: SonarQube Analysis ───────────────────────────────────────
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
                        sh """
                            cd executionEngine-service && ./mvnw -B sonar:sonar \\
                              -Dsonar.projectKey=${params.SONAR_PROJECT_KEY} \\
                              -Dsonar.host.url=${SONAR_HOST_URL} \\
                              -Dsonar.token=${SONAR_TOKEN} \\
                              -Dsonar.coverage.jacoco.xmlReportPaths=${JACOCO_XML_PATH}
                        """
                    }
                }
            }
        }

        // ── Stage 9: Quality Gate ─────────────────────────────────────────────
        stage('Quality Gate') {
            when {
                expression { return params.RUN_SONAR }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
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
                sh 'cd executionEngine-service && ./mvnw -B package -DskipTests'
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
        //  CD STAGES — Redesigned Multi-Env CD Flow
        // ─────────────────────────────────────────────────────────────────────

        // ── Stage 12: Resolve Deploy Environment ──────────────────────────────
        stage('Resolve Deploy Environment') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                script {
                    // Map branches to their respective Dev, QA, and Prod environments
                    if (env.GIT_BRANCH_NAME == 'develop') {
                        env.TARGET_ENV = 'dev'
                        env.RESOLVED_CLUSTER = 'my-cluster-dev'
                        env.RESOLVED_SERVICE = 'my-service-dev'
                        env.RESOLVED_TASK_FAMILY = 'my-task-dev'
                        env.RESOLVED_LOG_GROUP = '/ecs/my-task-dev'
                        env.RESOLVED_AWS_CREDS_ID = 'aws-creds-dev'
                    } else if (env.GIT_BRANCH_NAME ==~ /^release(\/.*)?$/) {
                        env.TARGET_ENV = 'qa'
                        env.RESOLVED_CLUSTER = 'my-cluster-qa'
                        env.RESOLVED_SERVICE = 'my-service-qa'
                        env.RESOLVED_TASK_FAMILY = 'my-task-qa'
                        env.RESOLVED_LOG_GROUP = '/ecs/my-task-qa'
                        env.RESOLVED_AWS_CREDS_ID = 'aws-creds-qa'
                    } else if (env.GIT_BRANCH_NAME == 'main' || env.GIT_BRANCH_NAME == 'master') {
                        env.TARGET_ENV = 'prod'
                        env.RESOLVED_CLUSTER = 'my-cluster-prod'
                        env.RESOLVED_SERVICE = 'my-service-prod'
                        env.RESOLVED_TASK_FAMILY = 'my-task-prod'
                        env.RESOLVED_LOG_GROUP = '/ecs/my-task-prod'
                        env.RESOLVED_AWS_CREDS_ID = 'aws-creds-prod'
                    } else {
                        error("Branch '${env.GIT_BRANCH_NAME}' is flagged for deployment but has no environment mapping in Resolve stage. Failing fast.")
                    }

                    env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMMUTABLE_TAG = "${env.TARGET_ENV}-${env.GIT_COMMIT_SHORT}-ci${env.BUILD_NUMBER}"
                    
                    echo "Target Environment  : ${env.TARGET_ENV}"
                    echo "Target Cluster      : ${env.RESOLVED_CLUSTER}"
                    echo "Target Service      : ${env.RESOLVED_SERVICE}"
                    echo "Target Task         : ${env.RESOLVED_TASK_FAMILY}"
                    echo "Immutable Image Tag : ${env.IMMUTABLE_TAG}"
                }
            }
        }

        // ── Stage 13: Build & Push Docker Image ───────────────────────────────
        stage('Build & Push Docker Image') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding', 
                    credentialsId: env.RESOLVED_AWS_CREDS_ID
                ]]) {
                    sh '''
                    echo "Building Docker image..."
                    docker build -t $ECR_REPO:$IMMUTABLE_TAG .

                    echo "Logging into ECR..."
                    aws ecr get-login-password --region $AWS_REGION \
                    | docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

                    echo "Pushing immutable tag to ECR..."
                    docker push $ECR_REPO:$IMMUTABLE_TAG
                    
                    echo "Applying and pushing environment latest tag..."
                    docker tag $ECR_REPO:$IMMUTABLE_TAG $ECR_REPO:${TARGET_ENV}-latest
                    docker push $ECR_REPO:${TARGET_ENV}-latest
                    '''
                }
            }
        }

        // ── Stage 14: Prod Approval Gate ──────────────────────────────────────
        // Will ONLY pause the pipeline if the target environment is 'prod'
        stage('Prod Approval Gate') {
            when {
                allOf {
                    expression { return env.TARGET_ENV == 'prod' }
                    expression { return params.RUN_PROD_APPROVAL }
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input message: "Deploy image ${env.IMMUTABLE_TAG} to PROD environment?", ok: 'Deploy'
                }
            }
        }

        // ── Stage 15: Deploy ──────────────────────────────────────────────────
        stage('Deploy') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                lock(resource: "cd-${env.TARGET_ENV}") {
                    withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding', 
                        credentialsId: env.RESOLVED_AWS_CREDS_ID
                    ]]) {
                        script {
                            // TODO: Implement Rollback functionality

                            stage('Prepare Task Definition') {
                                def taskDef = """{
  "family": "${env.RESOLVED_TASK_FAMILY}",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "executionRoleArn": "${env.EXECUTION_ROLE_ARN}",
  "taskRoleArn": "${env.TASK_ROLE_ARN}",
  "containerDefinitions": [
    {
      "name": "${env.TARGET_ENV}-my-app",
      "image": "${env.ECR_REPO}:${env.IMMUTABLE_TAG}",
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
          "awslogs-group": "${env.RESOLVED_LOG_GROUP}",
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

                            stage('Register Task Definition') {
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

                            stage('Deploy to ECS') {
                                sh """
                                echo "Deploying task def: ${env.RESOLVED_TASK_FAMILY}:${env.TASK_REVISION}"
                                aws ecs update-service \
                                    --region ${env.AWS_REGION} \
                                    --cluster ${env.RESOLVED_CLUSTER} \
                                    --service ${env.RESOLVED_SERVICE} \
                                    --task-definition ${env.RESOLVED_TASK_FAMILY}:${env.TASK_REVISION} \
                                    --health-check-grace-period-seconds 120 \
                                    --force-new-deployment
                                """
                            }

                            stage('Wait for Deployment') {
                                sh '''
                                echo "Waiting for ECS service to stabilize..."
                                aws ecs wait services-stable \
                                    --region $AWS_REGION \
                                    --cluster $RESOLVED_CLUSTER \
                                    --services $RESOLVED_SERVICE
                                '''
                            }
                        }
                    }
                }
            }
        }

        // ── Stage 16: Cleanup ─────────────────────────────────────────────────
        stage('Cleanup') {
            when {
                allOf {
                    expression { return params.RUN_DEPLOY }
                    expression { env.IS_DEPLOY_BRANCH == 'true' }
                }
            }
            steps {
                sh 'docker image prune -f'
            }
        }
    }

    post {
        success {
            script {
                if (env.IS_DEPLOY_BRANCH == 'true' && params.RUN_DEPLOY && env.TARGET_ENV) {
                    echo "✅ CI + CD successful — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER} | Env: ${env.TARGET_ENV} | Image: ${env.IMMUTABLE_TAG} | Task: ${env.RESOLVED_TASK_FAMILY}:${env.TASK_REVISION}"
                } else {
                    echo "✅ CI successful — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER} (CD skipped)"
                }
            }
        }
        failure {
            script {
                if (env.TARGET_ENV) {
                    echo "❌ Pipeline failed — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER} | Environment Context: ${env.TARGET_ENV}"
                } else {
                    echo "❌ Pipeline failed — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER}"
                }
            }
        }
        aborted {
            echo "⚠️ Pipeline aborted (e.g., Prod approval timeout or user cancellation) — Branch: ${env.GIT_BRANCH_NAME} | Build: #${env.BUILD_NUMBER}"
        }
        always {
            cleanWs()
        }
    }
}