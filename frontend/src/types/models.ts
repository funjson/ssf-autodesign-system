export interface ProjectDto {
  id: number;
  name: string;
  demoProject: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WorkspaceDto {
  id: number;
  projectId: number;
  path: string;
  displayName: string;
  sourceType: 'local' | 'imported' | 'demo' | string;
  createdAt: string;
  updatedAt: string;
  lastScannedAt?: string;
}

export interface FileTreeNodeDto {
  name: string;
  relativePath: string;
  directory: boolean;
  children: FileTreeNodeDto[];
}

export interface FileContentDto {
  name: string;
  relativePath: string;
  contentType: 'text' | 'markdown' | 'html' | 'json' | 'css' | 'code' | 'binary' | string;
  previewable: boolean;
  tooLarge: boolean;
  content: string;
}

export interface SpiInstanceDto {
  id: number;
  workspaceId: number;
  instanceCode: string;
  instanceName: string;
  productName: string;
  currentPhase: string;
  status: string;
  rootPath: string;
  manifestUpdatedAt: string;
  updatedAt: string;
}

export interface WorkspaceTreeDto {
  workspace: WorkspaceDto;
  instances: SpiInstanceDto[];
}

export interface ProjectTreeDto {
  project: ProjectDto;
  workspaces: WorkspaceTreeDto[];
}

export interface DocumentSourceDto {
  key: string;
  path: string;
  exists: boolean;
}

export interface PrototypeFrameDto {
  screenId: string;
  assetPath: string;
  assetUrl: string;
  exists: boolean;
}

export interface PrototypeSourceDto {
  sourceType: 'user-prototype' | 'generated-contract-preview' | string;
  label: string;
  description: string;
  rootPath: string;
  userProvided: boolean;
  frames: PrototypeFrameDto[];
}

export interface ScreenNodeDto {
  screenId: string;
  frameName: string;
  title: string;
  goal: string;
  entry: string;
  exit: string;
  relatedFeatures: string[];
  relatedRules: string[];
  relatedAcceptances: string[];
  componentIds: string[];
}

export interface ComponentNodeDto {
  componentId: string;
  screenId: string;
  name: string;
  visibleText: string;
  interaction: string;
  relationText: string;
  relatedFeatures: string[];
  relatedRules: string[];
  relatedAcceptances: string[];
  annotationIds: string[];
}

export interface FeatureNodeDto {
  featureId: string;
  name: string;
  goal: string;
  relatedScreensText: string;
  sourcePath: string;
  excerpt: string;
  metadata: KeyValueDto[];
  goals: string[];
  userStory: string;
  mainFlow: MainFlowStepDto[];
  branchFlows: BranchFlowDto[];
  exceptionFlows: ExceptionFlowDto[];
  sections: FeatureSectionDto[];
}

export interface TableDto {
  title: string;
  headers: string[];
  rows: string[][];
}

export interface FeatureSectionDto {
  sectionNo: string;
  title: string;
  content: string;
  bullets: string[];
  tables: TableDto[];
}

export interface KeyValueDto {
  key: string;
  value: string;
}

export interface MainFlowStepDto {
  step: string;
  actor: string;
  action: string;
  result: string;
  relatedUi: string;
}

export interface BranchFlowDto {
  branchId: string;
  trigger: string;
  flow: string;
  result: string;
  relatedRule: string;
}

export interface ExceptionFlowDto {
  exceptionId: string;
  scenario: string;
  systemBehavior: string;
  userTip: string;
  recovery: string;
  relatedAcceptance: string;
}

export interface RuleNodeDto {
  ruleId: string;
  featureId: string;
  type: string;
  trigger: string;
  rule: string;
  result: string;
  display: string;
  testFocus: string;
  sourcePath: string;
}

export interface AcceptanceNodeDto {
  acId: string;
  featureId: string;
  point: string;
  type: string;
  given: string;
  when: string;
  thenText: string;
  sourcePath: string;
}

export interface AnnotationNodeDto {
  pannoId: string;
  screenId: string;
  componentId?: string;
  layerName: string;
  note: string;
  relatedFeatures: string[];
  relatedRules: string[];
  relatedAcceptances: string[];
}

export interface FlowNodeDto {
  flowId: string;
  connection: string;
  note: string;
  relatedAcceptances: string[];
}

export interface SpecGraphDto {
  instanceId: number;
  instanceCode: string;
  instanceName: string;
  productName: string;
  rootPath: string;
  sources: DocumentSourceDto[];
  prototypeSource: PrototypeSourceDto;
  screens: ScreenNodeDto[];
  components: ComponentNodeDto[];
  features: FeatureNodeDto[];
  rules: RuleNodeDto[];
  acceptances: AcceptanceNodeDto[];
  annotations: AnnotationNodeDto[];
  flows: FlowNodeDto[];
  warnings: string[];
}

export interface ChangeRequestDto {
  id: number;
  spiInstanceId: number;
  changeCode: string;
  title: string;
  status: string;
  screenId: string;
  componentId: string;
  userIntent: string;
  extraNotes: string;
  filePath: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateChangeRequestPayload {
  title?: string;
  screenId?: string;
  componentId?: string;
  userIntent: string;
  extraNotes?: string;
}

export interface PrototypeUploadResult {
  savedCount: number;
  targetPath: string;
  overwritten: boolean;
  message: string;
}

export interface HtmlFileDto {
  path: string;
  name: string;
  entry: boolean;
}

export interface HtmlPrototypeTargetDto {
  targetId: string;
  kind: string;
  pagePath: string;
  screenSelector: string;
  selector: string;
  textFingerprint: string;
  tagName: string;
  elementId: string;
  className: string;
  rectX?: number;
  rectY?: number;
  rectWidth?: number;
  rectHeight?: number;
}

export interface HtmlPrototypeBindingDto {
  bindingId: string;
  name: string;
  prototypeType: string;
  screenId: string;
  componentId: string;
  targets: HtmlPrototypeTargetDto[];
  relatedFeatures: string[];
  relatedRules: string[];
  relatedAcceptances: string[];
}

export interface HtmlPrototypePackageDto {
  exists: boolean;
  packageRoot: string;
  sourceRoot: string;
  entryPath: string;
  entryUrl: string;
  htmlFiles: HtmlFileDto[];
  bindings: HtmlPrototypeBindingDto[];
  warnings: string[];
}

export interface HtmlPrototypeUploadResult {
  savedCount: number;
  overwritten: boolean;
  prototypePackage: HtmlPrototypePackageDto;
  message: string;
}

export interface HtmlElementSelection {
  targetId?: string;
  pagePath: string;
  selector: string;
  tagName: string;
  elementId: string;
  className: string;
  text: string;
  screenId: string;
  componentId: string;
  annotationIds: string[];
  featureIds: string[];
  ruleIds: string[];
  acceptanceIds: string[];
}

export interface SaveHtmlPrototypeBindingPayload {
  bindingId?: string;
  name?: string;
  screenId: string;
  componentId?: string;
  targets: HtmlPrototypeTargetDto[];
  relatedFeatures?: string[];
  relatedRules?: string[];
  relatedAcceptances?: string[];
}
