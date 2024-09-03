(ns infrastructure.main
  (:require
    ["@aws-cdk/region-info" :as region-info]
    ["aws-cdk-lib" :as cdk]
    ["aws-cdk-lib/aws-ecr" :as ecr]
    ["aws-cdk-lib/aws-ecr-assets" :as ecr-assets]
    ["aws-cdk-lib/aws-iam" :as iam]
    ["aws-cdk-lib/aws-imagebuilder" :as imagebuilder]
    ["aws-cdk-lib/aws-s3-assets" :as s3-assets]
    ["fs" :as fs]))

; GitHub Actions and GitLab Pipelines have default environment variables.
;
; https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/store-information-in-variables#default-environment-variables
;
; https://docs.gitlab.com/ee/ci/variables/predefined_variables.html
(def in-ci (= (.. js/process -env -CI) "true"))

(def app (cdk/App.))

(def stack (cdk/Stack. app "Production"))

(def ecr-public-repository
  (ecr/CfnPublicRepository.
    stack
    "ecr-public-repository"
    (clj->js
      {:emptyOnDelete true
       :repositoryName "nix-repository"})))

(def imagebuilder-role
  (iam/CfnRole.
    stack
    "imagebuilder-role"
    (clj->js
      {:assumeRolePolicyDocument
       {:Version "2012-10-17"
        :Statement
        [{:Effect "Allow"
          :Principal
          {:Service
           [(.servicePrincipalName iam/ServicePrincipal "ec2")]}
          :Action
          ["sts:AssumeRole"]}]}
       :description "EC2 Image Builder role."
       :managedPolicyArns
       [(.-managedPolicyArn (.fromAwsManagedPolicyName iam/ManagedPolicy "EC2InstanceProfileForImageBuilder"))
        (.-managedPolicyArn (.fromAwsManagedPolicyName iam/ManagedPolicy "EC2InstanceProfileForImageBuilderECRContainerBuilds"))]
       :roleName "imagebuilder-role"})))

(def imagebuilder-instance-profile
  (iam/CfnInstanceProfile.
    stack
    "imagebuilder-instance-profile"
    (clj->js
      {:roles
       [(.-ref imagebuilder-role)]
       :instanceProfileName "imagebuilder-instance-profile"})))

(def imagebuilder-infrastructure-configuration
  (imagebuilder/CfnInfrastructureConfiguration.
    stack
    "imagebuilder-infrastructure-configuration"
    (clj->js
      {:instanceProfileName (.-ref imagebuilder-instance-profile)
       :name "imagebuilder-infrastructure-configuration"})))

(def imagebuilder-distribution-configuration
  (imagebuilder/CfnDistributionConfiguration.
    stack
    "imagebuilder-distribution-configuration"
    (clj->js
      {:distributions
       (into
         []
         (concat
           [{:region (.-region stack)
             :containerDistributionConfiguration
             {:TargetRepository
              {:RepositoryName (.-ref ecr-public-repository)
               :Service "ECR"}}}]
           (->>
             (.-regions region-info/RegionInfo)
             ; Only use `aws` partition regions for now.
             (filter #(= (.-partition %) "aws"))
             ; Only use default regions for now.
             (filter #(not (.-isOptInRegion %)))
             (mapv
               #(identity
                  {:region (.-name %)
                   :amiDistributionConfiguration
                   {:LaunchPermissionConfiguration
                    {:UserGroups
                     ["all"]}}})))))
       :name "nix-image-distribution"})))

; Conditionally create image resources since systems without emulated native compilation may not have system-specific images.
(let
  [asset-path "outputs/main/oci-container/aarch64-linux/nix.tar.gz"]
  (when
    (or
      (fs/existsSync asset-path)
      in-ci)
    (def nix-aarch64-image-asset
      (ecr-assets/TarballImageAsset.
        stack
        "nix-aarch64-image-asset"
        (clj->js
          {:tarballFile asset-path})))

    ; TODO: No-op recipe with the tarball image asset as parent image.
    (def imagebuilder-image-nix-aarch64
      (imagebuilder/CfnImage.
        stack
        "imagebuilder-image-nix-aarch64"
        (clj->js
          {:infrastructureConfigurationArn (.-ref imagebuilder-infrastructure-configuration)
           :distributionConfigurationArn (.-ref imagebuilder-distribution-configuration)})))))

; Conditionally create image resources since systems without emulated native compilation may not have system-specific images.
(let
  [asset-path "outputs/main/oci-container/x86_64-linux/nix.tar.gz"]
  (when
    (or
      (fs/existsSync asset-path)
      in-ci)
    (def nix-x86-64-image-asset
      (ecr-assets/TarballImageAsset.
        stack
        "nix-x86-64-image-asset"
        (clj->js
          {:tarballFile asset-path})))

    ; TODO: No-op recipe with the tarball image asset as parent image.
    (def imagebuilder-image-nix-aarch64
      (imagebuilder/CfnImage.
        stack
        "imagebuilder-image-nix-x86-64"
        (clj->js
          {:infrastructureConfigurationArn (.-ref imagebuilder-infrastructure-configuration)
           :distributionConfigurationArn (.-ref imagebuilder-distribution-configuration)})))))

; Conditionally create image resources since systems without emulated native compilation may not have system-specific images.
(let
  [asset-path "outputs/main/virtual-machine/aarch64-linux/nixos.img"]
  (when
    (or
      (fs/existsSync asset-path)
      in-ci)
    (def nixos-aarch64-image-asset
      (s3-assets/Asset.
        stack
        "nixos-aarch64-image-asset"
        (clj->js
          {:path asset-path
           :deployTime true
           :followSymlinks (.-ALWAYS cdk/SymlinkFollowMode)})))

    ; TODO: Import a virtual machine image from S3.
    ;
    ; https://github.com/aws/ec2-image-builder-roadmap/issues/103
    (def imagebuilder-image-nixos-aarch64
      (imagebuilder/CfnImage.
        stack
        "imagebuilder-image-nixos-aarch64"
        (clj->js
          {:infrastructureConfigurationArn (.-ref imagebuilder-infrastructure-configuration)
           :distributionConfigurationArn (.-ref imagebuilder-distribution-configuration)})))))

; Conditionally create image resources since systems without emulated native compilation may not have system-specific images.
(let
  [asset-path "outputs/main/virtual-machine/x86_64-linux/nixos.img"]
  (when
    (or
      (fs/existsSync asset-path)
      in-ci)
    (def nixos-x86-64-image-asset
      (s3-assets/Asset.
        stack
        "nixos-x86-64-image-asset"
        (clj->js
          {:path asset-path
           :deployTime true
           :followSymlinks (.-ALWAYS cdk/SymlinkFollowMode)})))

    ; TODO: Import a virtual machine image from S3.
    ;
    ; https://github.com/aws/ec2-image-builder-roadmap/issues/103
    (def imagebuilder-image-nixos-x86-64
      (imagebuilder/CfnImage.
        stack
        "imagebuilder-image-nixos-x86-64"
        (clj->js
          {:infrastructureConfigurationArn (.-ref imagebuilder-infrastructure-configuration)
           :distributionConfigurationArn (.-ref imagebuilder-distribution-configuration)})))))

(def imagebuilder-lifecycle-role
  (iam/CfnRole.
    stack
    "imagebuilder-lifecycle-role"
    (clj->js
      {:assumeRolePolicyDocument
       {:Version "2012-10-17"
        :Statement
        [{:Effect "Allow"
          :Principal
          {:Service
           [(.servicePrincipalName iam/ServicePrincipal "ec2")]}
          :Action
          ["sts:AssumeRole"]}]}
       :description "EC2 Image Builder lifecycle role."
       :managedPolicyArns
       [(.-managedPolicyArn (.fromAwsManagedPolicyName iam/ManagedPolicy "EC2ImageBuilderLifecycleExecutionPolicy"))]
       :roleName "imagebuilder-lifecycle-role"})))

; TODO: May need a per-image lifecycle policy within each conditional.
(def imagebuilder-oci-container-lifecycle-policy
  (imagebuilder/CfnLifecyclePolicy.
    stack
    "imagebuilder-oci-container-lifecycle-policy"
    (clj->js
      {:executionRole (.-attrArn imagebuilder-lifecycle-role)
       :name "imagebuilder-oci-container-lifecycle-policy"
       :policyDetails
       [{:action
         {:includeResources
          {:containers true}
          :type "DELETE"}
         :filter
         {:type "AGE"
          :value 90
          :retainAtLeast 2
          :unit "DAYS"}}]
       :resourceSelection
       {}
       :resourceType "CONTAINER_IMAGE"
       :description "Deletes OCI container images older than 90 days except the 2 latest OCI container images (for CloudFormation stack rollback)."})))

; TODO: May need a per-image lifecycle policy within each conditional.
(def imagebuilder-ami-lifecycle-policy
  (imagebuilder/CfnLifecyclePolicy.
    stack
    "imagebuilder-ami-lifecycle-policy"
    (clj->js
      {:executionRole (.-attrArn imagebuilder-lifecycle-role)
       :name "imagebuilder-ami-lifecycle-policy"
       :policyDetails
       [{:action
         {:includeResources
          {:amis true
           :snapshots true}
          :type "DELETE"}
         :filter
         {:type "AGE"
          :value 90
          :retainAtLeast 2
          :unit "DAYS"}}]
       :resourceSelection
       {}
       :resourceType "AMI_IMAGE"
       :description "Deletes AMIs older than 90 days except the 2 latest AMIs (for CloudFormation stack rollback)."})))

(.synth app)
