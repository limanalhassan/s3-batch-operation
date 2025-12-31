folder('S3-Batch-Operations') {
    displayName('S3 Batch Operations')
    description('All jobs related to S3 batch operations (copy, restore, inventory, etc.)')
}

folder('S3-Batch-Operations/Dev') {
    displayName('Development')
    description('Development environment jobs')
}

println("Folders created successfully!")

// S3 Copy Job - References Jenkinsfile from jenkins/pipelines/
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
            script(readFileFromWorkspace('jenkins/pipelines/s3-copy.groovy'))
            sandbox(true)
        }
    }
}

println("Jobs created successfully!")
println("Seed job completed successfully!")

