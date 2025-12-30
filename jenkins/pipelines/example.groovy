/**
 * Example Jenkinsfile
 * 
 * This is a simple example pipeline that can be referenced by jobs created via Job DSL.
 * 
 * To use this in a Job DSL script:
 * script(readFileFromWorkspace('jenkins/pipelines/example.groovy'))
 */

pipeline {
    agent any
    
    options {
        // Discard old builds to save space
        buildDiscarder(logRotator(numToKeepStr: '10'))
        
        // Add timestamps to console output
        timestamps()
    }
    
    stages {
        stage('Hello') {
            steps {
                echo 'Hello from Job DSL!'
                echo "This pipeline was created by the seed job"
            }
        }
        
        stage('Environment Info') {
            steps {
                script {
                    echo "Jenkins URL: ${env.JENKINS_URL}"
                    echo "Job Name: ${env.JOB_NAME}"
                    echo "Build Number: ${env.BUILD_NUMBER}"
                    echo "Workspace: ${env.WORKSPACE}"
                }
            }
        }
        
        stage('AWS Info') {
            steps {
                script {
                    // Example: Get AWS account info
                    // sh 'aws sts get-caller-identity'
                    echo "AWS operations would go here"
                }
            }
        }
    }
    
    post {
        always {
            echo 'Pipeline completed!'
        }
        success {
            echo '✅ Pipeline succeeded!'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
    }
}

