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
 */

// Load folder definitions
// This creates the folder structure in Jenkins
def foldersScript = readFileFromWorkspace('jenkins/seed-job/folders.groovy')
evaluate(foldersScript)

// Load job definitions
// This creates the actual Jenkins jobs
def jobsScript = readFileFromWorkspace('jenkins/seed-job/jobs.groovy')
evaluate(jobsScript)

// Load view definitions (optional)
// This creates custom views to organize jobs
// Uncomment if you create views.groovy
// def viewsScript = readFileFromWorkspace('jenkins/seed-job/views.groovy')
// evaluate(viewsScript)

println("Seed job completed successfully!")
println("Folders and jobs have been created/updated.")

