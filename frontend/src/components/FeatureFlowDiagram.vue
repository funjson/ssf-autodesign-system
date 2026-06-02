<script setup lang="ts">
import type { BranchFlowDto, ExceptionFlowDto, MainFlowStepDto } from '../types/models';

defineProps<{
  mainFlow: MainFlowStepDto[];
  branchFlows: BranchFlowDto[];
  exceptionFlows: ExceptionFlowDto[];
}>();
</script>

<template>
  <div class="flow-diagrams">
    <section v-if="mainFlow.length" class="flow-section">
      <h4>主流程图</h4>
      <div class="main-flow">
        <article v-for="step in mainFlow" :key="step.step" class="flow-node">
          <span>{{ step.step }}</span>
          <strong>{{ step.actor }}</strong>
          <p>{{ step.action }}</p>
          <small>{{ step.result }}</small>
        </article>
      </div>
    </section>

    <section v-if="branchFlows.length" class="flow-section">
      <h4>分支流程图</h4>
      <article v-for="flow in branchFlows" :key="flow.branchId" class="branch-flow">
        <div>
          <span>触发</span>
          <p>{{ flow.trigger }}</p>
        </div>
        <div>
          <span>{{ flow.branchId }}</span>
          <p>{{ flow.flow }}</p>
        </div>
        <div>
          <span>结果</span>
          <p>{{ flow.result }}</p>
        </div>
      </article>
    </section>

    <section v-if="exceptionFlows.length" class="flow-section">
      <h4>异常流程图</h4>
      <article v-for="flow in exceptionFlows" :key="flow.exceptionId" class="exception-flow">
        <div>
          <span>{{ flow.exceptionId }}</span>
          <p>{{ flow.scenario }}</p>
        </div>
        <div>
          <span>系统行为</span>
          <p>{{ flow.systemBehavior }}</p>
        </div>
        <div>
          <span>用户提示</span>
          <p>{{ flow.userTip }}</p>
        </div>
        <div>
          <span>恢复</span>
          <p>{{ flow.recovery }}</p>
        </div>
      </article>
    </section>
  </div>
</template>
