import { computed, ref } from 'vue'
import { getChartTheme, monoScore } from '@/utils/chartTheme'

/**
 * 教师成绩分布图（分数段柱状图）
 * <p>从 useTeacherGrades 拆出的图表域（FE-14）：配置构建 + 分数入桶更新，
 * 与成绩数据加载解耦——编排层只喂 updateDistribution(scores)。</p>
 */
export function useTeacherGradeDistribution() {
  function buildDistributionOption() {
    const theme = getChartTheme()
    const bandScores = [95, 85, 75, 65, 50]
    return {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}人' },
      grid: { left: 50, right: 20, bottom: 30, top: 20 },
      xAxis: {
        type: 'category',
        data: ['90-100', '80-89', '70-79', '60-69', '0-59'],
        axisLine: { lineStyle: { color: theme.axis } },
        axisLabel: { color: theme.text, fontSize: 12 },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: theme.split, type: 'dashed' } },
        axisLabel: { color: theme.text, fontSize: 12 },
      },
      series: [
        {
          type: 'bar',
          data: [0, 0, 0, 0, 0],
          barWidth: '50%',
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: (params) => monoScore(bandScores[params.dataIndex] ?? 0),
          },
          label: { show: true, position: 'top', fontSize: 11, color: theme.text },
        },
      ],
    }
  }

  const distributionOption =
    ref<ReturnType<typeof buildDistributionOption>>(buildDistributionOption())
  const hasDistribution = computed(() => distributionOption.value.series[0].data.some((n) => n > 0))

  function updateDistribution(scores: number[]) {
    const buckets = [0, 0, 0, 0, 0]
    scores.forEach((score) => {
      if (score >= 90) buckets[0]++
      else if (score >= 80) buckets[1]++
      else if (score >= 70) buckets[2]++
      else if (score >= 60) buckets[3]++
      else buckets[4]++
    })
    distributionOption.value.series[0].data = buckets
  }

  return {
    distributionOption,
    hasDistribution,
    updateDistribution,
  }
}
