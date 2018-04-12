package com.convergencelabs.server.frontend.realtime

object MessageType extends Enumeration {
  val Error = 0

  val Ping = 1
  val Pong = 2

  val HandshakeRequest = 3
  val HandshakeResponse = 4

  val PasswordAuthRequest = 5
  val TokenAuthRequest = 6
  val AnonymousAuthRequest = 7
  val ReconnectAuthRequest = 8
  
  val AuthenticationResponse = 10

  val ReconnectTokenRequest = 20
  val ReconnectTokenResponse = 21
  
  // Models
  val OpenRealTimeModelRequest = 100
  val OpenRealTimeModelResponse = 101

  val CloseRealTimeModelRequest = 102
  val CloseRealTimeModelResponse = 103

  val CreateRealTimeModelRequest = 104
  val CreateRealTimeModelResponse = 105

  val DeleteRealtimeModelRequest = 106
  val DeleteRealtimeModelResponse = 107

  val ForceCloseRealTimeModel = 108

  val RemoteClientOpenedModel = 109
  val RemoteClientClosedModel = 110

  val ModelAutoCreateConfigRequest = 111
  val ModelAutoCreateConfigResponse = 112

  val RemoteOperation = 113

  val OperationSubmission = 114
  val OperationAck = 115

  val PublishReference = 116
  val SetReference = 117
  val ClearReference = 118
  val UnpublishReference = 119

  val ReferencePublished = 120
  val ReferenceSet = 121
  val ReferenceCleared = 122
  val ReferenceUnpublished = 123

  val ModelsQueryRequest = 124
  val ModelsQueryResponse = 125
  
  val HistoricalDataRequest = 126
  val HistoricalDataResponse = 127
  
  val HistoricalOperationsRequest = 128
  val HistoricalOperationsResponse = 129
  
  val GetModelPermissionsRequest = 130
  val GetModelPermissionsResponse = 131

  val SetModelPermissionsRequest = 132
  val SetModelPermissionsResponse = 133

  val ModelPermissionsChanged = 134
  
  // Identity
  val UserLookUpRequest = 200
  val UserSearchRequest = 201
  val UserListResponse = 202
  
  val UserGroupsRequest = 203
  val UserGroupsResponse = 204
  
  val UserGroupsForUsersRequest = 205
  val UserGroupsForUsersResponse = 206

  // Activity
  val ActivityParticipantsRequest = 300
  val ActivityParticipantsResponse = 301

  val ActivityJoinRequest = 302
  val ActivityJoinResponse = 303

  val ActivityLeaveRequest = 304
  val ActivityLeaveResponse = 305

  val ActivitySessionJoined = 306
  val ActivitySessionLeft = 307

  val ActivityLocalStateSet = 308
  val ActivityLocalStateRemoved = 309
  val ActivityLocalStateCleared = 310

  val ActivityRemoteStateSet = 311
  val ActivityRemoteStateRemoved = 312
  val ActivityRemoteStateCleared = 313
  
  // Presence
  val PresenceSetState = 400
  val PresenceRemoveState = 401
  val PresenceClearState = 402

  val PresenceStateSet = 403
  val PresenceStateRemoved = 404
  val PresenceStateCleared = 405

  val PresenceAvailabilityChanged = 406

  val PresenceRequest = 407
  val PresenceResponse = 408

  val PresenceSubscribeRequest = 409
  val PresenceSubscribeResponse = 410
  val PresenceUnsubscribe = 411
  
  // Chat
  val CreateChatChannelRequest = 500
  val CreateChatChannelResponse = 501

  val RemoveChatChannelRequest = 502
  val RemoveChatChannelResponse = 503
  val ChatChannelRemoved = 504

  val GetChatChannelsRequest = 505
  val GetChatChannelsResponse = 506
  
  val ChatChannelExistsRequest = 507
  val ChatChannelExistsResponse = 508

  val GetDirectChatChannelsRequest = 509
  val GetDirectChatChannelsResponse = 510

  val GetJoinedChatChannelsRequest = 511
  val GetJoinedChatChannelsResponse = 512

  val SearchChatChannelsRequest = 513
  val SearchChatChannelsResponse = 514

  val JoinChatChannelRequest = 515
  val JoinChatChannelResponse = 516
  val UserJoinedChatChannel = 517

  val LeaveChatChannelRequest = 518
  val LeaveChatChannelResponse = 519
  val UserLeftChatChannel = 520

  val AddUserToChatChannelRequest = 521
  val AddUserToChatChannelResponse = 522
  val UserAddedToChatChannel = 523

  val RemoveUserFromChatChannelRequest = 524
  val RemoveUserFromChatChannelResponse = 525
  val UserRemovedFromChatChannel = 526

  val SetChatChannelNameRequest = 527
  val SetChatChannelNameResponse = 528
  val ChatChannelNameChanged = 529

  val SetChatChannelTopicRequest = 530
  val SetChatChannelTopicResponse = 531
  val ChatChannelTopicChanged = 532

  val MarkChatChannelEventsSeenRequest = 533
  val MarkChatChannelEventsSeenResponse = 534
  val ChatChannelEventsMarkedSeen = 535

  val PublishChatMessageRequest = 536
  val PublishChatMessageResponse = 537
  val RemoteChatMessage = 538

  val GetChatChannelHistoryRequest = 539
  val GetChatChannelHistoryResponse = 540
  
  // Permissions
  val GetClientPermissionsRequest = 601
  val GetClientPermissionsResponse = 602
  
  val AddPermissionsRequest = 603
  val AddPermissionsResponse = 604
  
  val RemovePermissionsRequest = 605
  val RemovePermissionsResponse = 606
  
  val SetPermissionsRequest = 607
  val SetPermissionsResponse = 608
  
  val GetWorldPermissionsRequest = 609
  val GetWorldPermissionsResponse = 610
  
  val GetAllUserPermissionsRequest = 611
  val GetAllUserPermissionsResponse = 612
  
  val GetUserPermissionsRequest = 613
  val GetUserPermissionsResponse = 614
  
  val GetAllGroupPermissionsRequest = 615
  val GetAllGroupPermissionsResponse = 616
  
  val GetGroupPermissionsRequest = 617
  val GetGroupPermissionsResponse = 618
}
