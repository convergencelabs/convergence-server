package com.convergencelabs.server.frontend.realtime

import scalapb.GeneratedMessage
import io.convergence.proto.Request
import io.convergence.proto.Response
import io.convergence.proto.message.ConvergenceMessage.Body
import io.convergence.proto.connection._
import io.convergence.proto.model._
import io.convergence.proto.activity._
import io.convergence.proto.authentication._
import io.convergence.proto.model._
import io.convergence.proto.message._
import io.convergence.proto.common._
import io.convergence.proto.chat._
import io.convergence.proto.permissions._
import io.convergence.proto.presence._
import io.convergence.proto.references._
import io.convergence.proto.identity._
import io.convergence.proto.operations._

object ConvergenceMessageBodyUtils {
  def fromBody(body: Body): GeneratedMessage = {
    body match {
      case Body.Error(message) => message
      case Body.Ping(message) => message
      case Body.Pong(message) => message
      case Body.HandshakeRequest(message) => message
      case Body.HandshakeResponse(message) => message
      case Body.AuthenticationRequest(message) => message
      case Body.AuthenticationResponse(message) => message
      case Body.IdentityCacheUpdate(message) => message
      // Model
      case Body.OpenRealTimeModelRequest(message) => message
      case Body.OpenRealTimeModelResponse(message) => message
      case Body.CloseRealTimeModelRequest(message) => message
      case Body.CloseRealTimeModelResponse(message) => message
      case Body.CreateRealTimeModelRequest(message) => message
      case Body.CreateRealTimeModelResponse(message) => message
      case Body.DeleteRealtimeModelRequest(message) => message
      case Body.DeleteRealtimeModelResponse(message) => message
      case Body.ForceCloseRealTimeModel(message) => message
      case Body.RemoteClientOpenedModel(message) => message
      case Body.RemoteClientClosedModel(message) => message
      case Body.ModelAutoCreateConfigRequest(message) => message
      case Body.ModelAutoCreateConfigResponse(message) => message
      case Body.RemoteOperation(message) => message
      case Body.OperationSubmission(message) => message
      case Body.OperationAck(message) => message
      case Body.ShareReference(message) => message
      case Body.SetReference(message) => message
      case Body.ClearReference(message) => message
      case Body.UnshareReference(message) => message
      case Body.ReferenceShared(message) => message
      case Body.ReferenceSet(message) => message
      case Body.ReferenceCleared(message) => message
      case Body.ReferenceUnshared(message) => message
      case Body.ModelsQueryRequest(message) => message
      case Body.ModelsQueryResponse(message) => message
      case Body.HistoricalDataRequest(message) => message
      case Body.HistoricalDataResponse(message) => message
      case Body.HistoricalOperationsRequest(message) => message
      case Body.HistoricalOperationsResponse(message) => message
      case Body.GetModelPermissionsRequest(message) => message
      case Body.GetModelPermissionsResponse(message) => message
      case Body.SetModelPermissionsRequest(message) => message
      case Body.SetModelPermissionsResponse(message) => message
      case Body.ModelPermissionsChanged(message) => message
      // Identity
      case Body.UserLookUpRequest(message) => message
      case Body.UserSearchRequest(message) => message
      case Body.UserListResponse(message) => message
      case Body.UserGroupsRequest(message) => message
      case Body.UserGroupsResponse(message) => message
      case Body.UserGroupsForUsersRequest(message) => message
      case Body.UserGroupsForUsersResponse(message) => message
      // Activity
      case Body.ActivityParticipantsRequest(message) => message
      case Body.ActivityParticipantsResponse(message) => message
      case Body.ActivityJoinRequest(message) => message
      case Body.ActivityJoinResponse(message) => message
      case Body.ActivityLeaveRequest(message) => message
      case Body.ActivitySessionJoined(message) => message
      case Body.ActivitySessionLeft(message) => message
      case Body.ActivityLocalStateSet(message) => message
      case Body.ActivityLocalStateRemoved(message) => message
      case Body.ActivityLocalStateCleared(message) => message
      case Body.ActivityRemoteStateSet(message) => message
      case Body.ActivityRemoteStateRemoved(message) => message
      case Body.ActivityRemoteStateCleared(message) => message
      // Presence
      case Body.PresenceSetState(message) => message
      case Body.PresenceClearState(message) => message
      case Body.PresenceRemoveState(message) => message
      case Body.PresenceStateSet(message) => message
      case Body.PresenceStateCleared(message) => message
      case Body.PresenceStateRemoved(message) => message
      case Body.PresenceRequest(message) => message
      case Body.PresenceResponse(message) => message
      case Body.PresenceSubscribeRequest(message) => message
      case Body.PresenceSubscribeResponse(message) => message
      case Body.PresenceUnsubscribe(message) => message
      case Body.PresenceAvailabilityChanged(message) => message
      case Body.CreateChatChannelRequest(message) => message
      case Body.CreateChatChannelResponse(message) => message
      case Body.RemoveChatChannelRequest(message) => message
      case Body.RemoveChatChannelResponse(message) => message
      case Body.GetChatChannelsRequest(message) => message
      case Body.GetChatChannelsResponse(message) => message
      case Body.ChatChannelExistsRequest(message) => message
      case Body.ChatChannelExistsResponse(message) => message
      case Body.GetDirectChatChannelsRequest(message) => message
      case Body.GetDirectChatChannelsResponse(message) => message
      case Body.GetJoinedChatChannelsRequest(message) => message
      case Body.GetJoinedChatChannelsResponse(message) => message
      case Body.JoinChatChannelRequest(message) => message
      case Body.JoinChatChannelResponse(message) => message
      case Body.UserJoinedChatChannel(message) => message
      case Body.LeaveChatChannelRequest(message) => message
      case Body.LeaveChatChannelResponse(message) => message
      case Body.UserLeftChatChannel(message) => message
      case Body.AddUserToChatChannelRequest(message) => message
      case Body.AddUserToChatChannelResponse(message) => message
      case Body.UserAddedToChatChannel(message) => message
      case Body.RemoveUserFromChatChannelRequest(message) => message
      case Body.RemoveUserFromChatChannelResponse(message) => message
      case Body.UserRemovedFromChatChannel(message) => message
      case Body.SetChatChannelNameRequest(message) => message
      case Body.SetChatChannelNameResponse(message) => message
      case Body.ChatChannelNameChanged(message) => message
      case Body.SetChatChannelTopicRequest(message) => message
      case Body.SetChatChannelTopicResponse(message) => message
      case Body.ChatChannelTopicChanged(message) => message
      case Body.MarkChatChannelEventsSeenRequest(message) => message
      case Body.MarkChatChannelEventsSeenResponse(message) => message
      case Body.ChatChannelEventsMarkedSeen(message) => message
      case Body.PublishChatMessageRequest(message) => message
      case Body.PublishChatMessageResponse(message) => message
      case Body.RemoteChatMessage(message) => message
      case Body.GetChatChannelHistoryRequest(message) => message
      case Body.GetChatChannelHistoryResponse(message) => message
      case Body.ChatChannelRemoved(message) => message
      case Body.GetClientPermissionsRequest(message) => message
      case Body.GetClientPermissionsResponse(message) => message
      case Body.AddPermissionsRequest(message) => message
      case Body.AddPermissionsResponse(message) => message
      case Body.RemovePermissionsRequest(message) => message
      case Body.RemovePermissionsResponse(message) => message
      case Body.SetPermissionsRequest(message) => message
      case Body.SetPermissionsResponse(message) => message
      case Body.GetWorldPermissionsRequest(message) => message
      case Body.GetWorldPermissionsResponse(message) => message
      case Body.GetAllUserPermissionsRequest(message) => message
      case Body.GetAllUserPermissionsResponse(message) => message
      case Body.GetUserPermissionsRequest(message) => message
      case Body.GetUserPermissionsResponse(message) => message
      case Body.GetAllGroupPermissionsRequest(message) => message
      case Body.GetAllGroupPermissionsResponse(message) => message
      case Body.GetGroupPermissionsRequest(message) => message
      case Body.GetGroupPermissionsResponse(message) => message
      case Body.Empty => ???
    }
  }

  def toBody(message: GeneratedMessage): Body = {
    message match {
      case message: ErrorMessage => Body.Error(message)
      case message: PingMessage => Body.Ping(message)
      case message: PongMessage => Body.Pong(message)
      case message: HandshakeRequestMessage => Body.HandshakeRequest(message)
      case message: HandshakeResponseMessage => Body.HandshakeResponse(message)
      case message: AuthenticationRequestMessage => Body.AuthenticationRequest(message)
      case message: AuthenticationResponseMessage => Body.AuthenticationResponse(message)
      case message: IdentityCacheUpdateMessage => Body.IdentityCacheUpdate(message)
      // Model
      case message: OpenRealtimeModelRequestMessage => Body.OpenRealTimeModelRequest(message)
      case message: OpenRealtimeModelResponseMessage => Body.OpenRealTimeModelResponse(message)
      case message: CloseRealtimeModelRequestMessage => Body.CloseRealTimeModelRequest(message)
      case message: CloseRealTimeModelSuccessMessage => Body.CloseRealTimeModelResponse(message)
      case message: CreateRealtimeModelRequestMessage => Body.CreateRealTimeModelRequest(message)
      case message: CreateRealtimeModelSuccessMessage => Body.CreateRealTimeModelResponse(message)
      case message: DeleteRealtimeModelRequestMessage => Body.DeleteRealtimeModelRequest(message)
      case message: DeleteRealtimeModelSuccessMessage => Body.DeleteRealtimeModelResponse(message)
      case message: ModelForceCloseMessage => Body.ForceCloseRealTimeModel(message)
      case message: RemoteClientOpenedMessage => Body.RemoteClientOpenedModel(message)
      case message: RemoteClientClosedMessage => Body.RemoteClientClosedModel(message)
      case message: AutoCreateModelConfigRequestMessage => Body.ModelAutoCreateConfigRequest(message)
      case message: AutoCreateModelConfigResponseMessage => Body.ModelAutoCreateConfigResponse(message)
      case message: RemoteOperationMessage => Body.RemoteOperation(message)
      case message: OperationSubmissionMessage => Body.OperationSubmission(message)
      case message: OperationAcknowledgementMessage => Body.OperationAck(message)
      case message: ShareReferenceMessage => Body.ShareReference(message)
      case message: SetReferenceMessage => Body.SetReference(message)
      case message: ClearReferenceMessage => Body.ClearReference(message)
      case message: UnshareReferenceMessage => Body.UnshareReference(message)
      case message: RemoteReferenceSharedMessage => Body.ReferenceShared(message)
      case message: RemoteReferenceSetMessage => Body.ReferenceSet(message)
      case message: RemoteReferenceClearedMessage => Body.ReferenceCleared(message)
      case message: RemoteReferenceUnsharedMessage => Body.ReferenceUnshared(message)
      case message: ModelsQueryRequestMessage => Body.ModelsQueryRequest(message)
      case message: ModelsQueryResponseMessage => Body.ModelsQueryResponse(message)
      case message: HistoricalDataRequestMessage => Body.HistoricalDataRequest(message)
      case message: HistoricalDataResponseMessage => Body.HistoricalDataResponse(message)
      case message: HistoricalOperationRequestMessage => Body.HistoricalOperationsRequest(message)
      case message: HistoricalOperationsResponseMessage => Body.HistoricalOperationsResponse(message)
      case message: GetModelPermissionsRequestMessage => Body.GetModelPermissionsRequest(message)
      case message: GetModelPermissionsResponseMessage => Body.GetModelPermissionsResponse(message)
      case message: SetModelPermissionsRequestMessage => Body.SetModelPermissionsRequest(message)
      case message: SetModelPermissionsResponseMessage => Body.SetModelPermissionsResponse(message)
      case message: ModelPermissionsChangedMessage => Body.ModelPermissionsChanged(message)
      // Identity
      case message: UserLookUpMessage => Body.UserLookUpRequest(message)
      case message: UserSearchMessage => Body.UserSearchRequest(message)
      case message: UserListMessage => Body.UserListResponse(message)
      case message: UserGroupsRequestMessage => Body.UserGroupsRequest(message)
      case message: UserGroupsResponseMessage => Body.UserGroupsResponse(message)
      case message: UserGroupsForUsersRequestMessage => Body.UserGroupsForUsersRequest(message)
      case message: UserGroupsForUsersResponseMessage => Body.UserGroupsForUsersResponse(message)
      // Activity
      case message: ActivityParticipantsRequestMessage => Body.ActivityParticipantsRequest(message)
      case message: ActivityParticipantsResponseMessage => Body.ActivityParticipantsResponse(message)
      case message: ActivityJoinRequestMessage => Body.ActivityJoinRequest(message)
      case message: ActivityJoinResponseMessage => Body.ActivityJoinResponse(message)
      case message: ActivityLeaveMessage => Body.ActivityLeaveRequest(message)
      case message: ActivitySessionJoinedMessage => Body.ActivitySessionJoined(message)
      case message: ActivitySessionLeftMessage => Body.ActivitySessionLeft(message)
      case message: ActivitySetStateMessage => Body.ActivityLocalStateSet(message)
      case message: ActivityRemoveStateMessage => Body.ActivityLocalStateRemoved(message)
      case message: ActivityClearStateMessage => Body.ActivityLocalStateCleared(message)
      case message: ActivityRemoteStateSetMessage => Body.ActivityRemoteStateSet(message)
      case message: ActivityRemoteStateRemovedMessage => Body.ActivityRemoteStateRemoved(message)
      case message: ActivityRemoteStateClearedMessage => Body.ActivityRemoteStateCleared(message)
      // Presence
      case message: PresenceSetStateMessage => Body.PresenceSetState(message)
      case message: PresenceClearStateMessage => Body.PresenceClearState(message)
      case message: PresenceRemoveStateMessage => Body.PresenceRemoveState(message)
      case message: PresenceStateSetMessage => Body.PresenceStateSet(message)
      case message: PresenceStateClearedMessage => Body.PresenceStateCleared(message)
      case message: PresenceStateRemovedMessage => Body.PresenceStateRemoved(message)
      case message: PresenceRequestMessage => Body.PresenceRequest(message)
      case message: PresenceResponseMessage => Body.PresenceResponse(message)
      case message: SubscribePresenceRequestMessage => Body.PresenceSubscribeRequest(message)
      case message: SubscribePresenceResponseMessage => Body.PresenceSubscribeResponse(message)
      case message: UnsubscribePresenceMessage => Body.PresenceUnsubscribe(message)
      case message: PresenceAvailabilityChangedMessage => Body.PresenceAvailabilityChanged(message)
      // Chat
      case message: CreateChatChannelRequestMessage => Body.CreateChatChannelRequest(message)
      case message: CreateChatChannelResponseMessage => Body.CreateChatChannelResponse(message)
      case message: RemoveChatChannelRequestMessage => Body.RemoveChatChannelRequest(message)
      case message: RemoveChatChannelResponseMessage => Body.RemoveChatChannelResponse(message)
      case message: GetChatChannelsRequestMessage => Body.GetChatChannelsRequest(message)
      case message: GetChatChannelsResponseMessage => Body.GetChatChannelsResponse(message)
      case message: ChatChannelsExistsRequestMessage => Body.ChatChannelExistsRequest(message)
      case message: ChatChannelsExistsResponseMessage => Body.ChatChannelExistsResponse(message)
      case message: GetDirectChannelsRequestMessage => Body.GetDirectChatChannelsRequest(message)
      case message: GetDirectChannelsResponseMessage => Body.GetDirectChatChannelsResponse(message)
      case message: GetJoinedChatChannelsRequestMessage => Body.GetJoinedChatChannelsRequest(message)
      case message: GetJoinedChatChannelsResponseMessage => Body.GetJoinedChatChannelsResponse(message)
      case message: JoinChatChannelRequestMessage => Body.JoinChatChannelRequest(message)
      case message: JoinChatChannelResponseMessage => Body.JoinChatChannelResponse(message)
      case message: UserJoinedChatChannelMessage => Body.UserJoinedChatChannel(message)
      case message: LeaveChatChannelRequestMessage => Body.LeaveChatChannelRequest(message)
      case message: LeaveChatChannelResponseMessage => Body.LeaveChatChannelResponse(message)
      case message: UserLeftChatChannelMessage => Body.UserLeftChatChannel(message)
      case message: AddUserToChatChannelRequestMessage => Body.AddUserToChatChannelRequest(message)
      case message: AddUserToChatChannelResponseMessage => Body.AddUserToChatChannelResponse(message)
      case message: UserAddedToChatChannelMessage => Body.UserAddedToChatChannel(message)
      case message: RemoveUserFromChatChannelRequestMessage => Body.RemoveUserFromChatChannelRequest(message)
      case message: RemoveUserFromChatChannelResponseMessage => Body.RemoveUserFromChatChannelResponse(message)
      case message: UserRemovedFromChatChannelMessage => Body.UserRemovedFromChatChannel(message)
      case message: SetChatChannelNameRequestMessage => Body.SetChatChannelNameRequest(message)
      case message: SetChatChannelNameResponseMessage => Body.SetChatChannelNameResponse(message)
      case message: ChatChannelNameSetMessage => Body.ChatChannelNameChanged(message)
      case message: SetChatChannelTopicRequestMessage => Body.SetChatChannelTopicRequest(message)
      case message: SetChatChannelTopicResponseMessage => Body.SetChatChannelTopicResponse(message)
      case message: ChatChannelTopicSetMessage => Body.ChatChannelTopicChanged(message)
      case message: MarkChatChannelEventsSeenRequestMessage => Body.MarkChatChannelEventsSeenRequest(message)
      case message: MarkChatChannelEventsSeenResponseMessage => Body.MarkChatChannelEventsSeenResponse(message)
      case message: ChatChannelEventsMarkedSeenMessage => Body.ChatChannelEventsMarkedSeen(message)
      case message: PublishChatRequestMessage => Body.PublishChatMessageRequest(message)
      case message: PublishChatResponseMessage => Body.PublishChatMessageResponse(message)
      case message: RemoteChatMessageMessage => Body.RemoteChatMessage(message)
      case message: ChatChannelHistoryRequestMessage => Body.GetChatChannelHistoryRequest(message)
      case message: ChatChannelHistoryResponseMessage => Body.GetChatChannelHistoryResponse(message)
      case message: ChatChannelRemovedMessage => Body.ChatChannelRemoved(message)
      // Permissions
      case message: GetClientPermissionsRequestMessage => Body.GetClientPermissionsRequest(message)
      case message: GetClientPermissionsResponseMessage => Body.GetClientPermissionsResponse(message)
      case message: AddPermissionsRequestMessage => Body.AddPermissionsRequest(message)
      case message: AddPermissionsResponseMessage => Body.AddPermissionsResponse(message)
      case message: RemovePermissionsRequestMessage => Body.RemovePermissionsRequest(message)
      case message: RemovePermissionsResponseMessage => Body.RemovePermissionsResponse(message)
      case message: SetPermissionsRequestMessage => Body.SetPermissionsRequest(message)
      case message: SetPermissionsResponseMessage => Body.SetPermissionsResponse(message)
      case message: GetWorldPermissionsRequestMessage => Body.GetWorldPermissionsRequest(message)
      case message: GetWorldPermissionsResponseMessage => Body.GetWorldPermissionsResponse(message)
      case message: GetAllUserPermissionsRequestMessage => Body.GetAllUserPermissionsRequest(message)
      case message: GetAllUserPermissionsResponseMessage => Body.GetAllUserPermissionsResponse(message)
      case message: GetUserPermissionsRequestMessage => Body.GetUserPermissionsRequest(message)
      case message: GetUserPermissionsResponseMessage => Body.GetUserPermissionsResponse(message)
      case message: GetAllGroupPermissionsRequestMessage => Body.GetAllGroupPermissionsRequest(message)
      case message: GetAllGroupPermissionsResponseMessage => Body.GetAllGroupPermissionsResponse(message)
      case message: GetGroupPermissionsRequestMessage => Body.GetGroupPermissionsRequest(message)
      case message: GetGroupPermissionsResponseMessage => Body.GetGroupPermissionsResponse(message)
    }
  }
}