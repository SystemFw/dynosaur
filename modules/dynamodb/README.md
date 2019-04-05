# DynamoDB library (name TBD)

This module will be split up into its own library, whose purpose is a purely functional, non blocking api over DynamoDB

## Design

### Data model

Attribute value ADT
scodec style codecs, with lots of explicit combinators
potentially implicits and shapeless bindings, but behind an import, explicit combinators are preferred

### Low level api
One to one mapping to the dynamo api, modelling the case classes to reflect the dynamo entities
final tagless + http4s/circe

### High level api
Combinators for the common cases like retry on ThroughPutExceededException
higher level api, exposing e.g. Stream for `scan`, default parameters when sensible, automatic retry and so on


## Scope
Operations we will support:
  - BatchGetItem
  - BatchWriteItem
  - DeleteItem
  - GetItem
  - PutItem
  - Query
  - Scan
  - TransactGetItems
  - TransactWriteItems
  - UpdateItem

Operations we are not planning to write, but will accept a PR for:
  - CreateBackup
  - CreateGlobalTable
  - CreateTable
  - DeleteBackup
  - DeleteTable
  - DescribeBackup
  - DescribeContinuousBackups
  - DescribeGlobalTable
  - DescribeGlobalTableSettings
  - DescribeLimits
  - DescribeTable
  - DescribeTimeToLive
  - ListBackups
  - ListGlobalTables
  - ListTables
  - ListTagsOfResource
  - RestoreTableFromBackup
  - RestoreTableToPointInTime
  - TagResource
  - UntagResource
  - UpdateContinuousBackups
  - UpdateGlobalTable
  - UpdateGlobalTableSettings
  - UpdateTable
  - UpdateTimeToLive

Operations that we are not planning to support, typically because they depend on external infrastructure:
  - DescribeStream
  - GetShardIterator
  - GetRecords
  - ListStreams
  - CreateCluster
  - CreateParameterGroup
  - CreateSubnetGroup
  - DecreaseReplicationFactor
  - DeleteCluster
  - DeleteParameterGroup
  - DeleteSubnetGroup
  - DescribeClusters
  - DescribeDefaultParameters
  - DescribeEvents
  - DescribeParameterGroups
  - DescribeParameters
  - DescribeSubnetGroups
  - IncreaseReplicationFactor
  - ListTags
  - RebootNode
  - TagResource
  - UntagResource
  - UpdateCluster
  - UpdateParameterGroup
  - UpdateSubnetGroup




