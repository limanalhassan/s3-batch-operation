folder('S3-Batch-Operations') {
    displayName('S3 Batch Operations')
    description('All jobs related to S3 batch operations (copy, restore, inventory, etc.)')
}

folder('S3-Batch-Operations/Dev') {
    displayName('Development')
    description('Development environment jobs')
}

println("Folders created successfully!")

pipelineJob('S3-Batch-Operations/Dev/S3-Batch-Copy') {
    displayName('S3 Batch Copy Operation')
    description('Create S3 Batch Operations job to copy objects from source to destination bucket')
    
    parameters {
        stringParam('ACCOUNT_NAME', '', 'Account Name (e.g., liman) - Account number will be auto-selected')
        stringParam('S3_BATCH_INFRA_ROLE_NAME', '', 'Name of Role 2 (S3 Batch Infrastructure Role)')
        stringParam('ENV_TAG', '', 'Environment tag value to filter buckets (e.g., dev, staging, prod)')
        stringParam('SOURCE_PREFIX', '', 'Bucket prefix to copy from')
        stringParam('DEST_PREFIX', '', 'Bucket prefix to copy to')
        stringParam('REGION', 'us-east-1', 'AWS Region')
        stringParam('PRIORITY', '2', 'Job priority (higher number = higher priority)')
    }
    
    definition {
        cps {
            script(readFileFromWorkspace('jenkins/pipelines/s3-batch-copy.groovy'))
            sandbox(true)
        }
    }
}

println("Jobs created successfully!")
println("Seed job completed successfully!")

