package br.com.investlog.server.shared.utils

import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel

fun <T : Any> pagedModelOf(content: List<T>, pageable: Pageable, total: Long): PagedModel<T> =
    PagedModel(PageImpl(content, pageable, total))
