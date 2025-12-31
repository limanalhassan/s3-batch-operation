/**
 * S3 Copy Pipeline
 * 
 * This pipeline copies objects from a source S3 bucket to a destination bucket.
 * 
 * Parameters:
 * - SOURCE_BUCKET: Source S3 bucket name
 * - DEST_BUCKET: Destination S3 bucket name
 * - PREFIX: Object prefix filter (optional)
 * - DRY_RUN: Dry run mode (no actual copy)
 */

pipeline {
    agent any
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
    }
    
    stages {
        stage('Validate Parameters') {
            steps {
                script {
                    if (!params.SOURCE_BUCKET || !params.DEST_BUCKET) {
                        error('SOURCE_BUCKET and DEST_BUCKET are required parameters')
                    }
                    echo "Source Bucket: ${params.SOURCE_BUCKET}"
                    echo "Destination Bucket: ${params.DEST_BUCKET}"
                    echo "Prefix Filter: ${params.PREFIX ?: 'None (all objects)'}"
                    echo "Dry Run: ${params.DRY_RUN}"
                }
            }
        }
        
        stage('Copy S3 Objects') {
            steps {
                script {
                    def prefixArg = params.PREFIX ? "--prefix ${params.PREFIX}" : ""
                    def dryRunArg = params.DRY_RUN ? "--dryrun" : ""
                    
                    if (params.DRY_RUN) {
                        echo "DRY RUN MODE - No actual copy will be performed"
                        echo "Would copy from: s3://${params.SOURCE_BUCKET}${params.PREFIX ? '/' + params.PREFIX : ''}"
                        echo "Would copy to: s3://${params.DEST_BUCKET}"
                    } else {
                        echo "Copying objects from s3://${params.SOURCE_BUCKET} to s3://${params.DEST_BUCKET}"
                        // Add your actual S3 copy logic here
                        // Example: sh "aws s3 sync s3://${params.SOURCE_BUCKET}${params.PREFIX ? '/' + params.PREFIX : ''} s3://${params.DEST_BUCKET} ${prefixArg}"
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo 'S3 Copy operation completed!'
        }
        success {
            echo 'S3 Copy succeeded!'
        }
        failure {
            echo 'S3 Copy failed!'
        }
    }
}

