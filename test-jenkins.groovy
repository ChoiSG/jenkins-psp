pipeline { 
    agent any

    environment { 
        TEST="Hardcoding everything for now"
    }

    stages {
        stage('Cleanup'){
            steps{
                deleteDir()
            }
        }

        stage('Jenkins-check'){
            steps{
                echo "Hello, Jenkins"
            }
        }
        
        stage('Python-check'){
            steps{
                bat """python --version"""
            }
        }

        stage('Powershell-check'){
            steps{
                powershell(returnStdout:true, script:"echo 'Hello Powershell'")
            }
        }

        stage('Git-check'){
            steps{
                git branch:'main', url: 'https://github.com/GhostPack/Certify.git'
            }
        }

        stage('nuget-check'){
            steps{
                bat "nuget restore ${WORKSPACE}\\Certify.sln"
            }
        }

        stage('MSBuild-check'){
            steps{
                bat "\"${tool 'MSBuild_VS2019'}\\MSBuild.exe\" /p:Configuration=Release \"/p:Platform=Any CPU\" /maxcpucount:%NUMBER_OF_PROCESSORS% /nodeReuse:false /p:TargetFrameworkMoniker=\".NETFramework,Version=v4.8\" ${WORKSPACE}\\Certify.sln" 
            }
        }

        stage('Final-check'){
            steps{
                echo "Hello compiled Certify.exe!"
                bat "dir ${WORKSPACE}\\Certify\\bin\\Release\\"
            }
        }
    }
}