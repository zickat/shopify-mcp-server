package com.zickat.shopifymcpserver.pages.domain

import com.zickat.shopifymcpserver.pages.domain.repositories.PageSummary

data class ListPagesResult(
    val pages: List<PageSummary>,
    val truncated: Boolean,
    val hadQuery: Boolean,
)
