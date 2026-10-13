package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import com.leekyungmi.apm.apm_server.domain.event.entity.QEvent;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public double findAvgDurationsMs(String appId, LocalDateTime from, LocalDateTime to){
        QEvent e =  QEvent.event;
        Double result = jpaQueryFactory
                .select(e.durationMs.avg())
                .from(e)
                .where(appIdEq(appId), occurredAtGoe(from), occurredAtLoe(to))
                .fetchOne();
        return result !=null ? result : 0.0;
    }

    @Override
    public double findErrorRate(String appId, LocalDateTime from, LocalDateTime to){
        QEvent e =  QEvent.event;
        Long total = jpaQueryFactory.select(e.count()).from(e)
                .where(appIdEq(appId), occurredAtGoe(from), occurredAtLoe(to))
                .fetchOne();
        if(total == null || total == 0){return 0.0;}

        Long errorCount = jpaQueryFactory.select(e.count()).from(e)
                .where(appIdEq(appId), occurredAtGoe(from), occurredAtLoe(to), e.statusCode.goe(400))
                .fetchOne();
        return errorCount != null ? (errorCount * 100.0 / total) : 0.0;
    }

    @Override
    public Map<EventType, Long> countByType(String appId, LocalDateTime from, LocalDateTime to){
        QEvent e = QEvent.event;
        List<Tuple> rows = jpaQueryFactory
                .select(e.type, e.count())
                .from(e)
                .where(appIdEq(appId), occurredAtGoe(from), occurredAtLoe(to))
                .groupBy(e.type)
                .fetch();

        Map<EventType, Long> result = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            result.put(type, 0L);
        }
        for (Tuple row : rows) {
            result.put(row.get(e.type), row.get(e.count()));
        }
        return result;
    }

    @Override
    public List<String> findDistinctAppIds(){
        QEvent e = QEvent.event;
        return jpaQueryFactory
                .select(e.appId)
                .from(e)
                .distinct()
                .fetch();
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
