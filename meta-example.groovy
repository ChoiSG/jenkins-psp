pipeline { 
    agent any

    parameters {
        string(
            name: 'ProjectID',
            description: "Project ID for the engagement"
        )
    }

    environment { 
        TEST="hello, world!"
    }

    stages{
        stage('Echo'){
            steps{
                script{
                echo "[*] Starting PSP toolkit building for ProjectID: ${params.ProjectID}"
                }
            }
        }
    
        stage('test'){
            steps{
                script{
                    parallel(
                        "meta-Rubeus" : {build job: 'meta-Rubeus', propagate: false, parameters: [string(name: 'ProjectID', value: "${params.ProjectID}")]},
                        "meta-Certify" : {build job: 'meta-Certify', propagate: false, parameters: [string(name: 'ProjectID', value: "${params.ProjectID}")]},
                    )
                }
            }
        }
    }
}