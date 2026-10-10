package com.anxin.home.genai;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import com.anxin.home.genai.action.PendingAction;
import com.anxin.home.genai.action.PendingActionService;
import com.anxin.home.genai.security.ToolRequestContext;
import com.anxin.home.genai.security.UserContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class HousekeepingTools {
    private final AIDataProvider dataProvider;
    private final PendingActionService pendingActions;

    HousekeepingTools(AIDataProvider dataProvider, PendingActionService pendingActions) {
        this.dataProvider = dataProvider;
        this.pendingActions = pendingActions;
    }

    @Tool(description = "查询家政平台中的客户。仅管理员可以查看全部客户")
    List<Map<String, Object>> listCustomers() {
        requireRole(UserContext.Role.ADMIN);
        return dataProvider.getCustomers();
    }

    @Tool(description = "查询指定客户保存的上门服务地址")
    List<Map<String, Object>> listCustomerAddresses(@ToolParam(description = "客户编号") int customerId) {
        requireCustomerAccess(customerId);
        return dataProvider.getCustomerAddresses(customerId);
    }

    @Tool(description = "查询当前可预约的家政服务项目、价格和计价方式")
    List<Map<String, Object>> listServiceItems() { return dataProvider.getServiceItems(); }

    @Tool(description = "按照服务、日期、开始时间和行政区查询可用家政人员")
    List<Map<String, Object>> findAvailableHousekeepers(
        int serviceItemId, LocalDate date, LocalTime startTime,
        @ToolParam(required = false, description = "服务地址所在行政区") String district) {
        return dataProvider.findAvailableHousekeepers(serviceItemId, date, startTime, district);
    }

    @Tool(description = "根据服务项目和数量计算后端实时价格，不会创建订单")
    Map<String, Object> calculatePrice(int serviceItemId, BigDecimal quantity) {
        return dataProvider.calculatePrice(serviceItemId, quantity);
    }

    @Tool(description = "准备创建家政预约。该工具只登记待确认操作，不会直接创建订单，确认信息由后端单独发送给页面")
    Map<String, Object> prepareBooking(BookingRequest request) {
        UserContext context = ToolRequestContext.require();
        requireCustomerAccess(request.customerId());
        validateBooking(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", request.customerId());
        payload.put("addressId", request.addressId());
        payload.put("serviceItemId", request.serviceItemId());
        if (request.housekeeperId() != null) payload.put("housekeeperId", request.housekeeperId());
        payload.put("serviceDate", request.serviceDate());
        payload.put("startTime", request.startTime());
        payload.put("endTime", request.endTime());
        payload.put("quantity", request.quantity());
        payload.put("customerRemark", request.customerRemark() == null ? "" : request.customerRemark());
        return pendingActions.prepare(context, PendingAction.Type.CREATE_BOOKING, payload);
    }

    @Tool(description = "查询指定客户的家政预约订单")
    List<Map<String, Object>> listCustomerBookings(int customerId) {
        requireCustomerAccess(customerId);
        return dataProvider.getCustomerBookings(customerId);
    }

    @Tool(description = "准备取消尚未开始的预约。该工具只登记待确认操作，不会直接取消订单，确认信息由后端单独发送给页面")
    Map<String, Object> prepareCancellation(long bookingId) {
        UserContext context = ToolRequestContext.require();
        Map<String, Object> booking = dataProvider.getBooking(bookingId);
        requireBookingCancellationAccess(context, booking);
        return pendingActions.prepare(context, PendingAction.Type.CANCEL_BOOKING, Map.of("bookingId", bookingId));
    }

    private void validateBooking(BookingRequest request) {
        if (request.serviceDate() == null || request.serviceDate().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("服务日期不能早于今天");
        if (request.startTime() == null || request.endTime() == null || !request.endTime().isAfter(request.startTime()))
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        if (request.quantity() == null || request.quantity().signum() <= 0)
            throw new IllegalArgumentException("服务数量必须大于 0");
    }

    private void requireCustomerAccess(int customerId) {
        UserContext context = ToolRequestContext.require();
        if (context.role() == UserContext.Role.ADMIN) return;
        if (context.role() == UserContext.Role.CUSTOMER
            && context.userId().equals(String.valueOf(customerId))) return;
        throw new SecurityException("无权访问该客户的数据");
    }

    private void requireBookingCancellationAccess(UserContext context, Map<String, Object> booking) {
        if (context.role() == UserContext.Role.ADMIN) return;
        Object customerId = booking.get("customerId");
        if (context.role() == UserContext.Role.CUSTOMER && customerId != null
            && context.userId().equals(customerId.toString())) return;
        throw new SecurityException("无权取消该预约");
    }

    private void requireRole(UserContext.Role role) {
        if (ToolRequestContext.require().role() != role) throw new SecurityException("当前角色无权执行该操作");
    }
}

record BookingRequest(int customerId, int addressId, int serviceItemId, Integer housekeeperId,
                      LocalDate serviceDate, LocalTime startTime, LocalTime endTime,
                      BigDecimal quantity, String customerRemark) {
}
