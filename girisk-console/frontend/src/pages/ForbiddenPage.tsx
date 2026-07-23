import { Button, Result } from 'antd';
import { Link } from 'react-router-dom';

export default function ForbiddenPage() {
  return (
    <Result
      status="403"
      title="403"
      subTitle="当前账号无权访问该页面"
      extra={
        <Link to="/girisk">
          <Button type="primary">返回总览</Button>
        </Link>
      }
    />
  );
}
