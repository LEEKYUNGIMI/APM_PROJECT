package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import com.leekyungmi.apm.apm_server.domain.event.entity.QEvent;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EventCustomRepositoryImpl implements EventCustomRepository{
    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Page<Event> search(EventSearch search, Pageable pageable) {
        QEvent e =  QEvent.event;

        List<Event> content = jpaQueryFactory
                .selectFrom(e)
                .where(
                        appIdEq(search.getAppId()),
                        typeEq(search.getType()),
                        uriContains(search.getUri()),
                        occurredAtGoe(search.getFrom()),
                        occurredAtLoe(search.getTo())
                )
                .orderBy(e.occurredAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = jpaQueryFactory
                .select(e.count())
                .from(e)
                .where(
                        appIdEq(search.getAppId()),
                        typeEq(search.getType()),
                        uriContains(search.getUri()),
                        occurredAtGoe(search.getFrom()),
                        occurredAtLoe(search.getTo())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }

    private BooleanExpression appIdEq(String appId){
        return StringUtils.hasText(appId) ? QEvent.event.appId.eq(appId):null;
    }

    private BooleanExpression typeEq(EventType type){
        return type != null ? QEvent.event.type.eq(type) : null;
    }

    private BooleanExpression uriContains(String uri){
        return StringUtils.hasText(uri) ? QEvent.event.uri.contains(uri) : null;
    }

    private BooleanExpression occurredAtGoe(LocalDateTime from){
        return from != null ? QEvent.event.occurredAt.goe(from) : null;
    }

    private BooleanExpression occurredAtLoe(LocalDateTime to){
        return to != null ? QEvent.event.occurredAt.loe(to) : null;
}


}
