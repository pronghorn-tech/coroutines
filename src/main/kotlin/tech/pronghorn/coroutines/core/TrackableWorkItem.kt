package tech.pronghorn.coroutines.core

class TrackableWorkItem<WorkType, ParentType : Trackable>(work: WorkType,
                                                          val parent: ParentType) : WorkItem<WorkType>(work)
