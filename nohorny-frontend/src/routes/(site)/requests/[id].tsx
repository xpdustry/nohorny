import { Meta } from '@solidjs/meta';
import type { RouteSectionProps } from '@solidjs/router';
import { clientOnly } from '@solidjs/web';
import { RequestLoading } from '../../../components/request';

// The page renders in the browser only. The server answers every request identifier with the prerendered shell
const RequestPage = clientOnly(() => import('../../../components/request-page'));

export default function RequestRoute(props: RouteSectionProps) {
  return (
    <div class="wrap py-10">
      <Meta name="robots" content="noindex, nofollow" />
      <RequestPage id={props.params.id ?? ''} fallback={<RequestLoading />} />
    </div>
  );
}
