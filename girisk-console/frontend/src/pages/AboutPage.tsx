import { Button, Card, Descriptions, Space, Typography } from 'antd';
import { ArrowLeftOutlined, ExportOutlined, GithubOutlined, MailOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import BrandMark from '../components/BrandMark';
import {
  DOCUMENT_TITLE,
  PRODUCT_CODE,
  PRODUCT_FAMILY,
  PRODUCT_GITHUB,
  PRODUCT_LICENSE,
  PRODUCT_MAINTAINER,
  PRODUCT_NAME,
  PRODUCT_SUBTITLE,
  PRODUCT_TAGLINE,
  PRODUCT_VERSION,
} from '../brand';

const { Title, Paragraph, Link, Text } = Typography;

export default function AboutPage() {
  const navigate = useNavigate();

  return (
    <div className="page-header" style={{ maxWidth: 720 }}>
      <Button
        type="text"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(-1)}
        style={{ marginBottom: 8, paddingLeft: 0 }}
      >
        返回
      </Button>
      <Card>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <BrandMark size={56} />
            <div>
              <Title level={3} style={{ margin: 0 }}>
                {PRODUCT_NAME}
              </Title>
              <Text type="secondary">
                {PRODUCT_SUBTITLE} · {PRODUCT_FAMILY} 产品族
              </Text>
            </div>
          </div>

          <Paragraph style={{ marginBottom: 0 }}>{PRODUCT_TAGLINE}</Paragraph>

          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="产品代号">{PRODUCT_CODE}</Descriptions.Item>
            <Descriptions.Item label="版本">{PRODUCT_VERSION}</Descriptions.Item>
            <Descriptions.Item label="许可证">{PRODUCT_LICENSE}</Descriptions.Item>
            <Descriptions.Item label="文档标题">{DOCUMENT_TITLE}</Descriptions.Item>
            <Descriptions.Item label="代码仓库">
              <Link href={PRODUCT_GITHUB} target="_blank" rel="noopener noreferrer">
                <GithubOutlined /> {PRODUCT_GITHUB.replace(/^https:\/\//, '')}
              </Link>
            </Descriptions.Item>
            <Descriptions.Item label="维护人">
              <span>{PRODUCT_MAINTAINER.name}</span>
              <Text type="secondary"> · </Text>
              <Link href={`mailto:${PRODUCT_MAINTAINER.email}`}>
                <MailOutlined /> {PRODUCT_MAINTAINER.email}
              </Link>
            </Descriptions.Item>
          </Descriptions>

          <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
            GiRisk（玑险）与 GIDO（玑渡）同属 gido 家族：统一决策、敞口监控、审计可回放。
            源代码以 Apache-2.0 发布；商标与品牌名称另有约定。
          </Paragraph>

          <Space wrap>
            <Button
              type="primary"
              icon={<ExportOutlined />}
              href={PRODUCT_GITHUB}
              target="_blank"
              rel="noopener noreferrer"
            >
              打开 GitHub
            </Button>
            <Button href={`mailto:${PRODUCT_MAINTAINER.email}`}>联系维护人</Button>
          </Space>
        </Space>
      </Card>
    </div>
  );
}
