package com.leekyungmi.apm.apm_server.domain.event.repository;

import com.leekyungmi.apm.apm_server.domain.event.EventType;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventDurationStats;
import com.leekyungmi.apm.apm_server.domain.event.dto.EventSearch;
import com.leekyungmi.apm.apm_server.domain.event.entity.Event;
import com.leekyungmi.apm.apm_server.domain.event.entity.QEvent;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
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
    public EventDurationStats findRequestDurationStats(String appId, LocalDateTime from, LocalDateTime to){
        QEvent e =  QEvent.event;
        NumberExpression<Double> avg = e.durationMs.avg();
        NumberExpression<Double> p95 = percentileCont(0.95);
        NumberExpression<Double> p99 = percentileCont(0.99);
        Tuple row = jpaQueryFactory
                .select(avg, p95, p99)
                .from(e)
                .where(requestTypes(), appIdEq(appId), occurredAtGoe(from), occurredAtLoe(to))
                .fetchOne();
        if (row == null) {
            return new EventDurationStats(0.0, 0.0, 0.0);
        }
        return new EventDurationStats(orZero(row.get(avg)), orZero(row.get(p95)), orZero(row.get(p99)));
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

    /** startTimeUs 오름차순(null이면 startTimeMs*1000으로 대체), 동률이면 id 오름차순. */
    @Override
    public List<Event> findTraceSpans(String traceId){
        QEvent e = QEvent.event;
        return jpaQueryFactory
                .selectFrom(e)
                .where(e.traceId.eq(traceId))
                .orderBy(e.startTimeUs.coalesce(e.startTimeMs.multiply(1000L)).asc(), e.id.asc())
                .fetch();
    }

    private BooleanExpression appIdEq(String appId){
        return StringUtils.hasText(appId) ? QEvent.event.appId.eq(appId):null;
    }

    /** type 미지정이면 요청 타입(HTTP_REQUEST·SLOW_REQUEST·ERROR)만. */
    private BooleanExpression typeEq(EventType type){
        return type != null ? QEvent.event.type.eq(type) : requestTypes();
    }

    private BooleanExpression requestTypes(){
        return QEvent.event.type.in(EventType.REQUEST_TYPES);
    }

    /**
     * PostgreSQL percentile_cont — Hibernate 6 HQL의 WITHIN GROUP 구문으로 전달된다.
     * 결과 타입이 정렬 대상 타입(int)으로 잡혀 소수점이 잘리므로 double로 캐스팅해 정렬한다.
     */
    private NumberExpression<Double> percentileCont(double fraction){
        return Expressions.numberTemplate(Double.class,
                "percentile_cont(" + fraction + ") within group (order by cast({0} as double))", QEvent.event.durationMs);
    }

    private double orZero(Double value){
        return value != null ? value : 0.0;
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
