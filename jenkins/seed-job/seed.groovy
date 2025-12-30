/**
 * Main Seed Job Script
 * 
 * This is the entry point for the Job DSL seed job.
 * It loads and executes other Groovy files to create folders, jobs, and views.
 * 
 * How it works:
 * 1. Seed job checks out this repository
 * 2. Runs this script (seed.groovy)
 * 3. This script loads folders.groovy, jobs.groovy, etc.
 * 4. Those scripts create the actual Jenkins items
 * 
 * NOTE: If you get "No signature of method" errors, use seed-inline.groovy instead,
 * or configure the Job DSL build step to use "Look on Filesystem" option.
 */

// Directly include the folder definitions
// This creates the folder structure in Jenkins
folder('S3-Batch-Operations') {
    displayName('S3 Batch Operations')
    description('All jobs related to S3 batch operations (copy, restore, inventory, etc.)')
}

folder('S3-Batch-Operations/Dev') {
    displayName('Development')
    description('Development environment jobs')
}

println("Folders created successfully!")

// Directly include job definitions
// This creates the actual Jenkins jobs
pipelineJob('S3-Batch-Operations/Dev/Example-Pipeline') {
    displayName('Example Pipeline Job')
    description('An example pipeline job to demonstrate Job DSL')
    
    definition {
        cps {
            script(readFileFromWorkspace('jenkins/pipelines/example.groovy'))
            sandbox(true)
        }
    }
    
    triggers {
        cron('H * * * *')
    }
    
    parameters {
        stringParam('BUCKET_NAME', 'my-bucket', 'S3 bucket name')
        choiceParam('ENVIRONMENT', ['dev', 'staging', 'prod'], 'Target environment')
    }
}

freeStyleJob('S3-Batch-Operations/Dev/Example-Freestyle') {
    displayName('Example Freestyle Job')
    description('An example freestyle job')
    
    steps {
        shell('''
            echo "Hello from Job DSL!"
            echo "This is a freestyle job created by the seed job"
        ''')
    }
    
    publishers {
        archiveArtifacts('**/*')
    }
}

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
        cron('0 2 * * *')
    }
}

println("Jobs created successfully!")
println("Seed job completed successfully!")

