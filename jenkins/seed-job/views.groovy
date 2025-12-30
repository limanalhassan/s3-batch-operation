/**
 * View Definitions (Optional)
 * 
 * This script creates custom views to organize and display jobs in Jenkins.
 * Views are like "dashboards" that show specific jobs.
 * 
 * Uncomment the evaluate() line in seed.groovy to use this.
 */

// Create a list view for all S3 Batch Operations jobs
listView('S3 Batch Operations') {
    description('All S3 Batch Operations jobs')
    
    // Filter jobs by name pattern
    jobs {
        regex(/^S3-Batch-Operations.*/)
    }
    
    // Columns to display
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

// Create a view for Dev environment only
listView('S3 Batch Operations - Dev') {
    description('Development environment jobs')
    
    jobs {
        regex(/^S3-Batch-Operations\/Dev.*/)
    }
    
    columns {
        status()
        name()
        lastSuccess()
        lastFailure()
    }
}

println("Views created successfully!")

