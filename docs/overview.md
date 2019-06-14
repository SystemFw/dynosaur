---
id: overview
title: Overview
---

Dynosaur is a purely functional, native, non-blocking client for DynamoDb, based on cats-effect and fs2

NOTE: Dynosaur right now is still in internal development, it will follow a proper OSS governance model once bootstrapped.

## Design

### Data model

Attribute value ADT
codec model based on schema (Xenomorph style), potentially enabling schema derivation

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



