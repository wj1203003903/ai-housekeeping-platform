package com.anxin.home.genai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import com.anxin.home.genai.security.ToolRequestContext;
import com.anxin.home.genai.security.UserContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * The only tools visible to the orchestrator. Each method delegates to a
 * specialist ChatClient that owns a deliberately small business-tool set.
 */
@Component
class SpecialistAgents {
    private final ChatClient solutionAdvisor;
    private final ChatClient bookingCoordinator;
    private final ChatClient afterSalesSpecialist;

    SpecialistAgents(ChatModel chatModel, SolutionAdvisorTools solutionTools,
                     BookingCoordinatorTools bookingTools, AfterSalesTools afterSalesTools) {
        this.solutionAdvisor = ChatClient.builder(chatModel)
            .defaultSystem("""
                你是家政方案顾问 Agent，负责预约前的复杂需求分析和服务组合设计。
                你必须通过工具读取真实服务目录；需要个性化建议时，可以查询当前客户的地址和历史预约。
                你负责判断和解释，不负责排班、最终计价、创建订单或取消订单。
                用户需求不完整时，明确列出仍需补充的信息，不得猜测面积、地址、时长、预算或服务类型。
                推荐结果应包含服务名称、推荐原因、建议顺序、预计工作量和注意事项。
                """)
            .defaultTools(solutionTools)
            .build();

        this.bookingCoordinator = ChatClient.builder(chatModel)
            .defaultSystem("""
                你是预约调度 Agent，负责把已经明确的服务需求转换为可确认的预约方案。
                你必须通过工具查询客户地址、真实服务目录、可用服务人员并由后端计算价格。
                缺少服务项目、日期、开始和结束时间、地址或数量时，只返回缺失信息，不得猜测。
                只有用户已经明确选择方案并要求预约时，才能调用准备预约工具。
                准备预约只会登记待确认操作，不代表订单已经创建；必须提示用户再次确认，不得生成或输出确认令牌。
                不得声称人员已锁定、价格已支付或预约已成功。
                """)
            .defaultTools(bookingTools)
            .build();

        this.afterSalesSpecialist = ChatClient.builder(chatModel)
            .defaultSystem("""
                你是履约售后 Agent，负责订单创建后的状态解释、取消咨询、改派建议和服务质量问题分流。
                你必须先通过工具查询当前客户的真实订单，不得读取其他客户数据。
                只有用户明确要求取消具体订单时，才能调用准备取消工具。
                准备取消只会登记待确认操作，不代表订单已经取消；必须提示用户再次确认，不得生成或输出确认令牌。
                当前系统没有自动退款或自动处罚工具，遇到退款、投诉和责任认定只能给出处理建议并提示转人工。
                """)
            .defaultTools(afterSalesTools)
            .build();
    }

    @Tool(description = "委派家政方案顾问分析复杂需求并设计服务方案。适用于服务类型不明确、需要组合服务、预算或家庭场景分析；不处理排班和下单")
    String delegateSolutionDesign(String requirement) {
        return solutionAdvisor.prompt()
            .user(taskPrompt(requirement))
            .call()
            .content();
    }

    @Tool(description = "委派预约调度专员查询地址、真实价格和可用人员，形成预约选项；信息完整且用户明确要求预约时可准备确认令牌")
    String delegateBookingCoordination(String requirement) {
        return bookingCoordinator.prompt()
            .user(taskPrompt(requirement))
            .call()
            .content();
    }

    @Tool(description = "委派履约售后专员查询当前客户订单，处理状态说明、取消准备、改派建议和服务质量问题；退款或投诉需要转人工")
    String delegateAfterSales(String requirement) {
        return afterSalesSpecialist.prompt()
            .user(taskPrompt(requirement))
            .call()
            .content();
    }

    private String taskPrompt(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("委派给专业 Agent 的任务不能为空");
        }
        UserContext context = ToolRequestContext.require();
        return "当前日期：" + LocalDate.now()
            + "\n已认证用户编号：" + context.userId()
            + "\n已认证角色：" + context.role()
            + "\n主控 Agent 委派的任务：" + requirement;
    }
}
