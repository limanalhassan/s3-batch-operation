/**
 * Job Definitions
 * 
 * This script creates actual Jenkins jobs.
 * 
 * Types of jobs you can create:
 * 1. pipelineJob - Pipeline jobs (Jenkinsfile)
 * 2. freeStyleJob - Freestyle jobs (GUI-configured)
 * 3. multibranchPipelineJob - Multi-branch pipelines
 * 
 * Each job can reference:
 * - Jenkinsfiles from the repository
 * - Build triggers
 * - Parameters
 * - Notifications
 * - etc.
 */

// Example 1: Simple Pipeline Job
// This creates a pipeline job that runs a Jenkinsfile
pipelineJob('S3-Batch-Operations/Dev/Example-Pipeline') {
    displayName('Example Pipeline Job')
    description('An example pipeline job to demonstrate Job DSL')
    
    // Define the pipeline
    definition {
        cps {
            // Reference a Jenkinsfile from the repository
            script(readFileFromWorkspace('jenkins/pipelines/example.groovy'))
            sandbox(true) // Run in sandbox for security
        }
    }
    
    // Configure when this job should run
    triggers {
        // Run every hour
        cron('H * * * *')
        
        // OR run on SCM changes (uncomment if needed)
        // scm('H/5 * * * *')
    }
    
    // Add parameters (optional)
    parameters {
        stringParam('BUCKET_NAME', 'my-bucket', 'S3 bucket name')
        choiceParam('ENVIRONMENT', ['dev', 'staging', 'prod'], 'Target environment')
    }
}

// Example 2: Freestyle Job
// This creates a traditional freestyle job
freeStyleJob('S3-Batch-Operations/Dev/Example-Freestyle') {
    displayName('Example Freestyle Job')
    description('An example freestyle job')
    
    // Add build steps
    steps {
        shell('''
            echo "Hello from Job DSL!"
            echo "This is a freestyle job created by the seed job"
        ''')
    }
    
    // Add post-build actions
    publishers {
        // Archive artifacts
        archiveArtifacts('**/*')
        
        // Email notification (uncomment and configure if needed)
        // mailer('team@example.com', false, true)
    }
}

// Example 3: S3 Copy Job Template
// This is a template you can customize for actual S3 operations
pipelineJob('S3-Batch-Operations/Dev/S3-Copy') {
    displayName('S3 Copy Operation')
    description('Copy objects from source to destination bucket')
    
    parameters {
        stringParam('SOURCE_BUCKET', '', 'Source S3 bucket name')
        stringParam('DEST_BUCKET', '', 'Destination S3 bucket name')
        stringParam('PREFIX', '', 'Object prefix filter (optional)')
        booleanParam('DRY_RUN', true, 'Dry run mode (no actual copy)')
    }
    
    definition {
        cps {
            script('''
                pipeline {
                    agent any
                    stages {
                        stage('Copy S3 Objects') {
                            steps {
                                script {
                                    echo "Copying from ${SOURCE_BUCKET} to ${DEST_BUCKET}"
                                    // Add your S3 copy logic here
                                    // Example: aws s3 sync s3://${SOURCE_BUCKET} s3://${DEST_BUCKET}
                                }
                            }
                        }
                    }
                }
            ''')
            sandbox(true)
        }
    }
}

// Example 4: S3 Inventory Job
pipelineJob('S3-Batch-Operations/Dev/S3-Inventory') {
    displayName('S3 Inventory Report')
    description('Generate inventory report for S3 buckets')
    
    parameters {
        stringParam('BUCKET_NAME', '', 'S3 bucket name to inventory')
        choiceParam('REPORT_FORMAT', ['json', 'csv'], 'Report format')
    }
    
    definition {
        cps {
            script('''
                pipeline {
                    agent any
                    stages {
                        stage('Generate Inventory') {
                            steps {
                                script {
                                    echo "Generating inventory for ${BUCKET_NAME}"
                                    // Add inventory generation logic here
                                }
                            }
                        }
                    }
                }
            ''')
            sandbox(true)
        }
    }
    
    triggers {
        // Run daily at 2 AM
        cron('0 2 * * *')
    }
}

println("Jobs created successfully!")

