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

  val OpenRealTimeModelRequest = 8
  val OpenRealTimeModelResponse = 9

  val CloseRealTimeModelRequest = 10
  val CloseRealTimeModelResponse = 11

  val CreateRealTimeModelRequest = 12
  val CreateRealTimeModelResponse = 13

  val DeleteRealtimeModelRequest = 14
  val DeleteRealtimeModelResponse = 15

  val ForceCloseRealTimeModel = 16

  val RemoteClientOpenedModel = 17
  val RemoteClientClosedModel = 18

  val ModelDataRequest = 19
  val ModelDataResponse = 20

  val RemoteOperation = 21

  val OperationSubmission = 22
  val OperationAck = 23

  val PublishReference = 24
  val SetReference = 25
  val ClearReference = 26
  val UnpublishReference = 27

  val ReferencePublished = 28
  val ReferenceSet = 29
  val ReferenceCleared = 30
  val ReferenceUnpublished = 31

  val UserLookUpRequest = 50
  val UserSearchRequest = 51
  val UserListResponse = 52
  
  val ActivityParticipantsRequest = 60
  val ActivityParticipantsResponse = 61

  val ActivityJoinRequest = 64
  val ActivityJoinResponse = 65

  val ActivityLeaveRequest = 66
  val ActivityLeaveResponse = 67

  val ActivitySessionJoined = 68
  val ActivitySessionLeft = 69

  val ActivityLocalStateSet = 70
  val ActivityLocalStateRemoved = 71
  val ActivityLocalStateCleared = 72

  val ActivityRemoteStateSet = 73
  val ActivityRemoteStateRemoved = 74
  val ActivityRemoteStateCleared = 75
  
  val PresenceSetState = 76
  val PresenceClearState = 77

  val PresenceStateSet = 78
  val PresenceStateCleared = 79

  val PresenceAvailabilityChanged = 80

  val PresenceRequest = 81
  val PresenceResponse = 82

  val PresenceSubscribeRequest = 83
  val PresenceSubscribeResponse = 84
  val PresenceUnsubscribe = 85
  
  val JoinRoomRequest = 86
  val JoinRoomResponse = 87
  val LeaveRoom = 88
  val PublishChatMessage = 89

  val UserJoinedRoom = 90
  val UserLeftRoom = 91
  val ChatMessagePublished = 92
  
  val ModelsQueryRequest = 93
  val ModelsQueryResponse = 94
  
  val HistoricalDataRequest = 95
  val HistoricalDataResponse = 96
  
  val HistoricalOperationsRequest = 97
  val HistoricalOperationsResponse = 98
}
