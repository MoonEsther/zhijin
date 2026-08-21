import { useState } from 'react';
import { Card, Pagination, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import { AuditItem, auditApi } from '../api/audit';

const PAGE_SIZE = 20;

/** 审计日志页：分页展示操作审计记录（audit:view）。 */
export function AuditPage() {
  // 受控分页：页码变化触发重新查询（queryKey 带页码，天然缓存各页）
  const [page, setPage] = useState(1);

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', page, PAGE_SIZE],
    queryFn: () => auditApi.page(page, PAGE_SIZE),
  });

  const items = data?.items ?? [];
  const total = data?.total ?? 0;

  const columns: ColumnsType<AuditItem> = [
    { title: '时间', dataIndex: 'createTime', width: 180, render: (v?: string) => v ?? '-' },
    { title: '用户', dataIndex: 'username', width: 140 },
    { title: '动作', dataIndex: 'action', width: 160 },
    // 对象列：目标类型 + 目标 ID 组合展示（如 APP:12）
    {
      title: '对象',
      key: 'target',
      width: 160,
      render: (_, record) => (record.targetId != null ? `${record.targetType}:${record.targetId}` : record.targetType),
    },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
  ];

  return (
    <Card>
      <Typography.Title level={4} style={{ margin: 0, marginBottom: 16 }}>
        审计日志
      </Typography.Title>
      <Table
        rowKey={(record) => record.id ?? `${record.createTime}-${record.username}-${record.action}`}
        columns={columns}
        dataSource={items}
        loading={isLoading}
        pagination={false}
        size="small"
      />
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
        <Pagination
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          showSizeChanger={false}
          showTotal={(t) => `共 ${t} 条`}
          onChange={(p) => setPage(p)}
        />
      </div>
    </Card>
  );
}
