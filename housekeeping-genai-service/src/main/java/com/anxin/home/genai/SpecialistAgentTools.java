package com.anxin.home.genai;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Tool whitelist for the read-only service-plan specialist. */
@Component
class SolutionAdvisorTools {
    private final HousekeepingTools tools;

    SolutionAdvisorTools(HousekeepingTools tools) {
        this.tools = tools;
    }

    @Tool(description = "查询当前可预约的真实家政服务目录、单价、最低服务量和计价方式")
    List<Map<String, Object>> listServiceCatalog() {
        return tools.listServiceItems();
    }

    @Tool(description = "查询当前客户保存的服务地址，用于了解房屋区域和已有地址；严禁查询其他客户")
    List<Map<String, Object>> listCustomerAddresses(int customerId) {
        return tools.listCustomerAddresses(customerId);
    }

    @Tool(description = "查询当前客户历史预约，用于分析服务偏好；严禁查询其他客户")
    List<Map<String, Object>> listCustomerBookingHistory(int customerId) {
        return tools.listCustomerBookings(customerId);
    }
}

/** Tool whitelist for scheduling, trusted pricing and booking preparation. */
@Component
class BookingCoordinatorTools {
    private final HousekeepingTools tools;

    BookingCoordinatorTools(HousekeepingTools tools) {
        this.tools = tools;
    }

    @Tool(description = "查询当前客户保存的服务地址；预约使用的地址必须属于当前客户")
    List<Map<String, Object>> listCustomerAddresses(int customerId) {
        return tools.listCustomerAddresses(customerId);
    }

    @Tool(description = "查询真实服务目录，用于确定服务编号、计价单位和最低服务量")
    List<Map<String, Object>> listServiceCatalog() {
        return tools.listServiceItems();
    }

    @Tool(description = "根据服务、日期、开始时间和行政区查询后端实时可用的家政服务人员")
    List<Map<String, Object>> findAvailableHousekeepers(
        int serviceItemId, LocalDate date, LocalTime startTime,
        @ToolParam(required = false, description = "服务地址所在行政区") String district) {
        return tools.findAvailableHousekeepers(serviceItemId, date, startTime, district);
    }

    @Tool(description = "由后端根据服务项目和数量计算可信价格；不会创建订单")
    Map<String, Object> calculateTrustedPrice(int serviceItemId, BigDecimal quantity) {
        return tools.calculatePrice(serviceItemId, quantity);
    }

    @Tool(description = "准备创建预约。仅在信息完整且用户明确要求预约后调用；只登记待确认操作，不会创建订单，确认信息由后端单独发送给页面")
    Map<String, Object> prepareBooking(BookingRequest request) {
        return tools.prepareBooking(request);
    }
}

/** Tool whitelist for post-booking service and cancellation preparation. */
@Component
class AfterSalesTools {
    private final HousekeepingTools tools;

    AfterSalesTools(HousekeepingTools tools) {
        this.tools = tools;
    }

    @Tool(description = "查询当前客户的真实预约订单，用于状态解释和售后判断；严禁查询其他客户")
    List<Map<String, Object>> listCustomerBookings(int customerId) {
        return tools.listCustomerBookings(customerId);
    }

    @Tool(description = "准备取消尚未开始的预约。仅在用户明确指定订单并要求取消后调用；只登记待确认操作，不会直接取消，确认信息由后端单独发送给页面")
    Map<String, Object> prepareCancellation(long bookingId) {
        return tools.prepareCancellation(bookingId);
    }
}
