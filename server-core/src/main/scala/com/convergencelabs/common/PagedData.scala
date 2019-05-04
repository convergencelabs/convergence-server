package com.convergencelabs.common

case class PagedData[T](
  data: List[T],
  offset: Long,
  count: Long
)
