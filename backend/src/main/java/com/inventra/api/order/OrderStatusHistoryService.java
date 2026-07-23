package com.inventra.api.order;

import com.inventra.api.entity.Order;
import com.inventra.api.entity.OrderStatus;
import com.inventra.api.entity.OrderStatusHistory;
import com.inventra.api.entity.User;
import com.inventra.api.order.dto.OrderStatusHistoryResponse;
import com.inventra.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {
    
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderStatusHistoryMapper historyMapper;
    
    @Transactional
    public void createHistoryEntry(Order order, OrderStatus fromStatus, OrderStatus toStatus, User changedBy, String notes) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .notes(notes)
                .build();
        
        historyRepository.save(history);
        log.info("Created status history entry for order {} from {} to {}", order.getId(), fromStatus, toStatus);
    }
    
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderHistory(String orderId) {
        List<OrderStatusHistory> history = historyRepository
                .findByOrderIdAndTenantId(orderId, TenantContext.requireTenantId());
        return history.stream()
                .map(historyMapper::toResponse)
                .toList();
    }
}
