package com.example.vornx.support.entity;

import com.example.vornx.common.entity.BaseEntity;
import com.example.vornx.common.enums.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ticket_messages")
public class TicketMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private SupportTicket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20, updatable = false)
    private ActorType senderType;

    @Column(name = "sender_id", updatable = false)
    private Long senderId;

    @Column(name = "sender_name", nullable = false, length = 100, updatable = false)
    private String senderName;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String body;

    public TicketMessage(ActorType senderType, Long senderId, String senderName, String body) {
        this.senderType = senderType;
        this.senderId = senderId;
        this.senderName = senderName;
        this.body = body;
    }

    void attachTo(SupportTicket ticket) {
        this.ticket = ticket;
    }
}
