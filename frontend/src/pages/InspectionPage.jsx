import { useEffect, useState } from 'react';
import { useParams, Link, useSearchParams } from 'react-router-dom';
import {
  getProperty,
  getInspectionRooms,
  getInspections,
  createRoom,
} from '../api/client';
import Layout from '../components/Layout';
import InspectionTypeBadge from '../components/InspectionTypeBadge';
import ReportEditor from '../components/ReportEditor';

function InspectionPage() {
  const { propertyId, inspectionId } = useParams();
  const [property, setProperty] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [inspection, setInspection] = useState(null);
  const [loading, setLoading] = useState(true);

  // tab 状态放 URL,刷新不丢;两个 tab 同时挂载,切换不会丢失 Report 里未保存的编辑
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'report' ? 'report' : 'photos';

  function switchTab(next) {
    setSearchParams(next === 'photos' ? {} : { tab: next }, { replace: true });
  }

  // Add room 状态
  const [addingRoom, setAddingRoom] = useState(false);
  const [newRoomName, setNewRoomName] = useState('');
  const [savingRoom, setSavingRoom] = useState(false);

  useEffect(() => {
    loadData();
  }, [propertyId, inspectionId]);

  async function loadData() {
    setLoading(true);
    try {
      const [propertyData, roomsData, inspectionsData] = await Promise.all([
        getProperty(propertyId),
        getInspectionRooms(inspectionId),
        getInspections(propertyId),
      ]);
      setProperty(propertyData);
      setRooms(roomsData);
      setInspection(
        inspectionsData.find((i) => i.id === Number(inspectionId)) ?? null
      );
    } finally {
      setLoading(false);
    }
  }

  async function handleAddRoom() {
    const name = newRoomName.trim();
    if (!name || savingRoom) return;
    setSavingRoom(true);
    try {
      await createRoom(propertyId, name);
      setNewRoomName('');
      setAddingRoom(false);
      // 重新拉 inspection 语境的房间列表,新房间带着 photoCount 0 进入网格
      setRooms(await getInspectionRooms(inspectionId));
    } finally {
      setSavingRoom(false);
    }
  }

  function cancelAddRoom() {
    setNewRoomName('');
    setAddingRoom(false);
  }

  if (loading || !property) {
    return (
      <Layout>
        <div className="py-16 text-center text-slate-400">Loading...</div>
      </Layout>
    );
  }

  const photographed = rooms.filter((r) => r.photoCount > 0).length;

  const tabCls = (active) =>
    `px-1 pb-2 text-sm font-medium border-b-2 transition-colors ${
      active
        ? 'border-teal-700 text-teal-800'
        : 'border-transparent text-slate-400 hover:text-slate-600'
    }`;

  return (
    <Layout
      breadcrumbs={[
        { label: property.address, to: `/properties/${propertyId}` },
        {
          label: inspection
            ? `${inspection.type} — ${inspection.inspectionDate}`
            : 'Inspection',
        },
      ]}
    >
      {/* 页头 */}
      <div className="mb-6 flex items-center gap-3 flex-wrap">
        {inspection && <InspectionTypeBadge type={inspection.type} />}
        <h1 className="text-2xl font-semibold text-slate-900">
          {inspection ? inspection.inspectionDate : 'Inspection'}
        </h1>
        <span className="text-sm text-slate-400 ml-auto">
          {photographed} of {rooms.length} rooms photographed
        </span>
      </div>

      {/* Tab 栏 */}
      <div className="mb-6 flex gap-6 border-b border-slate-200">
        <button onClick={() => switchTab('photos')} className={tabCls(tab === 'photos')}>
          Photos
        </button>
        <button onClick={() => switchTab('report')} className={tabCls(tab === 'report')}>
          Report
        </button>
      </div>

      {/* Photos tab:房间网格,实心 = 有照片,虚线 = 待拍 */}
      <div className={tab === 'photos' ? '' : 'hidden'}>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
          {rooms.map((room) => {
            const hasPhotos = room.photoCount > 0;
            return (
              <Link
                key={room.id}
                to={`/properties/${propertyId}/inspections/${inspectionId}/rooms/${room.id}`}
                className={`rounded-xl p-4 flex items-center justify-between gap-2 transition-colors
                  ${hasPhotos
                    ? 'bg-white border border-slate-200 shadow-sm hover:border-teal-600'
                    : 'bg-slate-50 border border-dashed border-slate-300 hover:border-teal-600'
                  }`}
              >
                <span
                  className={`truncate ${hasPhotos ? 'text-slate-900 font-medium' : 'text-slate-500'}`}
                >
                  {room.name}
                </span>
                {hasPhotos ? (
                  <span className="shrink-0 min-w-7 h-7 px-2 rounded-full bg-teal-700 text-white
                                   text-sm font-semibold flex items-center justify-center">
                    {room.photoCount}
                  </span>
                ) : (
                  <span className="shrink-0 text-xs text-amber-600 font-medium">
                    No photos
                  </span>
                )}
              </Link>
            );
          })}

          {/* Add room:点击原地变输入框,Enter 创建,Esc 取消 */}
          {addingRoom ? (
            <div className="rounded-xl p-4 bg-white border border-teal-600 flex items-center">
              <input
                autoFocus
                type="text"
                value={newRoomName}
                onChange={(e) => setNewRoomName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleAddRoom();
                  if (e.key === 'Escape') cancelAddRoom();
                }}
                onBlur={() => {
                  if (!newRoomName.trim()) cancelAddRoom();
                }}
                placeholder={savingRoom ? 'Adding...' : 'Room name, then Enter'}
                disabled={savingRoom}
                className="w-full text-sm text-slate-900 placeholder:text-slate-400
                           focus:outline-none bg-transparent"
              />
            </div>
          ) : (
            <button
              onClick={() => setAddingRoom(true)}
              className="rounded-xl p-4 border border-dashed border-slate-300 text-slate-400
                         text-sm font-medium text-left
                         hover:border-teal-600 hover:text-teal-700 transition-colors"
            >
              + Add room
            </button>
          )}
        </div>
      </div>

      {/* Report tab */}
      <div className={tab === 'report' ? '' : 'hidden'}>
        <ReportEditor inspectionId={inspectionId} />
      </div>
    </Layout>
  );
}

export default InspectionPage;