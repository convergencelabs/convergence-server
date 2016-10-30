package com.convergencelabs.server.frontend.realtime

object MessageType extends Enumeration {
  val Error = 0

  val Ping = 1
  val Pong = 2

  val HandshakeRequest = 3
  val HandshakeResponse = 4

  val PasswordAuthRequest = 5
  val TokenAuthRequest = 6
  val AuthenticationResponse = 7

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

  val ModelDataRequest = 111
  val ModelDataResponse = 112

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
  
  // Identity
  val UserLookUpRequest = 200
  val UserSearchRequest = 201
  val UserListResponse = 202
  
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
  val JoinRoomRequest = 500
  val JoinRoomResponse = 501
  val LeaveRoom = 502
  val PublishChatMessage = 503

  val UserJoinedRoom = 504
  val UserLeftRoom = 505
  val ChatMessagePublished = 506
}
