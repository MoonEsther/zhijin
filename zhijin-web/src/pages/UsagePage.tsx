import { Card, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import { UsageSummary, usageApi } from '../api/usage';

/** 用量汇总页：按应用维度展示调用量/Token 消耗统计（usage:view）。 */
export function UsagePage() {
  // TanStack Query v5：queryKey 唯一化，queryFn 直接指向 api（request 已解包 Result<T>）
  const { data: usage = [], isLoading } = useQuery({
    queryKey: ['usage-summary'],
    queryFn: usageApi.summary,
  });

  // 汇总行合计：应用数量 + 调用量 + Token 消耗
  const totalCalls = usage.reduce((sum, it) => sum + it.totalCalls, 0);
  const totalTokens = usage.reduce((sum, it) => sum + it.totalTokens, 0);

  const columns: ColumnsType<UsageSummary> = [
    { title: '应用ID', dataIndex: 'appId', width: 160 },
    { title: '调用量', dataIndex: 'totalCalls', align: 'right' },
    { title: 'Token 消耗', dataIndex: 'totalTokens', align: 'right' },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ margin: 0, marginBottom: 16 }}>
        用量汇总
      </Typography.Title>
      <Table
        rowKey="appId"
        columns={columns}
        dataSource={usage}
        loading={isLoading}
        pagination={false}
        summary={() => (
          // antd 表格自带汇总行：底部合计调用量与 Token 消耗
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>合计（{usage.length} 个应用）</Table.Summary.Cell>
            <Table.Summary.Cell index={1} align="right">
              {totalCalls}
            </Table.Summary.Cell>
            <Table.Summary.Cell index={2} align="right">
              {totalTokens}
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Card>
  );
}
