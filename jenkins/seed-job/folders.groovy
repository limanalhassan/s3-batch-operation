/**
 * Folder Definitions
 * 
 * This script creates the folder hierarchy in Jenkins.
 * Folders help organize jobs by project, environment, or team.
 * 
 * Syntax:
 * folder('Path/To/Folder') {
 *     displayName('Display Name')
 *     description('Description of what this folder contains')
 * }
 */

// Main project folder
folder('S3-Batch-Operations') {
    displayName('S3 Batch Operations')
    description('All jobs related to S3 batch operations (copy, restore, inventory, etc.)')
}

// Environment-specific folders
folder('S3-Batch-Operations/Dev') {
    displayName('Development')
    description('Development environment jobs')
}

println("Folders created successfully!")

